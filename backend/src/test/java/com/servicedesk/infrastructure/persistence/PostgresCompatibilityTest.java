package com.servicedesk.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.incident.NewIncident;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs the Flyway migrations and Hibernate schema validation against a <b>real PostgreSQL</b>
 * server (started in-process by zonky embedded-postgres - no Docker needed), with the same settings
 * as production ({@code ddl-auto: validate}, PostgreSQL dialect). This catches anything H2's
 * PostgreSQL mode would let through, such as type differences in timestamp columns.
 */
@SpringBootTest
@Transactional
@DisplayName("PostgreSQL compatibility")
class PostgresCompatibilityTest {

  private static final EmbeddedPostgres POSTGRES = start();

  private static EmbeddedPostgres start() {
    try {
      return EmbeddedPostgres.start();
    } catch (IOException e) {
      throw new IllegalStateException("Could not start embedded PostgreSQL", e);
    }
  }

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
    r.add("spring.datasource.username", () -> "postgres");
    r.add("spring.datasource.password", () -> "postgres");
    r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    r.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
  }

  @AfterAll
  static void stop() throws IOException {
    POSTGRES.close();
  }

  @Autowired IncidentRepository incidents;
  @Autowired UserRepository users;
  @Autowired ItServiceRepository services;
  @Autowired CategoryRepository categories;

  @Test
  @DisplayName(
      "TC-85 migrations, schema validation and an incident round-trip work on real PostgreSQL")
  void worksOnPostgres() {
    // reaching this point means Flyway V1-V3 applied and Hibernate validated every entity
    IncidentFactory factory =
        new IncidentFactory(
            incidents, Clock.fixed(Instant.parse("2026-11-12T10:00:00.123456Z"), ZoneOffset.UTC));
    Incident i =
        factory.create(
            new NewIncident(
                users.get(new UserId("E101")),
                "Payment app unavailable",
                "Users cannot complete transactions since 10:00",
                services.get("PAYMENT"),
                Level.CRITICAL,
                Level.HIGH,
                300));
    i.classify(categories.get("APPLICATION"), SystemActor.INSTANCE, i.createdAt());
    i.prioritize(new MatrixPriorityStrategy(), SystemActor.INSTANCE, i.createdAt());
    i.assignTo((Technician) users.get(new UserId("T201")), SystemActor.INSTANCE, i.createdAt());
    incidents.save(i);

    Incident loaded = incidents.search(IncidentQuery.all().containing("payment app")).get(0);
    assertThat(loaded.snapshot()).isEqualTo(i.snapshot()); // incl. microsecond timestamps
    assertThat(((Technician) users.get(new UserId("T201"))).activeWorkload()).isEqualTo(1);
    assertThat(users.findTechnicians()).hasSize(8);
  }
}
