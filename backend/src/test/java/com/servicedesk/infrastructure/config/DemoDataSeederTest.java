package com.servicedesk.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.application.SlaMonitoringService;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the application with demo data enabled, against its own in-memory database so the other
 * tests keep an empty incident table.
 */
@SpringBootTest(
    properties = {
      "app.demo.enabled=true",
      "spring.datasource.url=jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH"
    })
@DisplayName("Demo data")
class DemoDataSeederTest {

  @Autowired IncidentRepository incidents;
  @Autowired AuditLog auditLog;
  @Autowired SlaMonitoringService slaMonitoring;

  @Test
  @DisplayName(
      "TC-99 demo data covers every lifecycle state with full audit trails, and the first SLA run"
          + " escalates")
  void demoData() {
    List<Incident> all = incidents.search(IncidentQuery.all().page(IncidentQuery.MAX_LIMIT, 0));
    assertThat(all).hasSize(22);
    Map<IncidentStatus, Long> byStatus =
        all.stream().collect(Collectors.groupingBy(Incident::status, Collectors.counting()));
    assertThat(byStatus)
        .containsKeys(
            IncidentStatus.NEW,
            IncidentStatus.ASSIGNED,
            IncidentStatus.ACKNOWLEDGED,
            IncidentStatus.IN_PROGRESS,
            IncidentStatus.AWAITING_INFO,
            IncidentStatus.RESOLVED,
            IncidentStatus.CLOSED,
            IncidentStatus.CANCELLED);
    Map<String, Incident> byTitle =
        all.stream().collect(Collectors.toMap(Incident::title, Function.identity()));
    Incident closed = byTitle.get("Payment gateway timing out at checkout");
    assertThat(auditLog.historyOf(closed.id()))
        .extracting(e -> e.action())
        .containsSubsequence("CREATED", "ASSIGNED", "RESOLVED", "FEEDBACK_GIVEN", "CLOSED");
    assertThat(closed.feedback()).isPresent();

    var run = slaMonitoring.runCheck();
    assertThat(run.violations()).isPositive();
    assertThat(run.escalations()).isPositive();
    assertThat(incidents.get(byTitle.get("Unable to make payment").id()).escalationLevel())
        .isEqualTo(2);
  }
}
