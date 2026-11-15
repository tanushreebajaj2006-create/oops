package com.servicedesk;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;

/**
 * Runs the full backend on http://localhost:8080 with a throw-away embedded PostgreSQL - no Docker
 * or database installation needed. Demo data and the SLA monitor are on. Data is lost when the
 * process stops.
 *
 * <p>From {@code backend/}:
 *
 * <pre>
 *   ./mvnw -q test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt -Dmdep.includeScope=test
 *   java -cp "target/classes;target/test-classes;$(cat target/cp.txt)" com.servicedesk.LocalDevApplication
 * </pre>
 *
 * (Use {@code :} instead of {@code ;} as the classpath separator on macOS/Linux.)
 */
public final class LocalDevApplication {

  private LocalDevApplication() {}

  public static void main(String[] args) throws Exception {
    EmbeddedPostgres postgres = EmbeddedPostgres.builder().start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    postgres.close();
                  } catch (Exception ignored) {
                    // shutting down anyway
                  }
                }));
    SpringApplication.run(
        ServiceDeskApplication.class,
        "--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
        "--spring.datasource.username=postgres",
        "--spring.datasource.password=postgres",
        "--spring.datasource.driver-class-name=org.postgresql.Driver",
        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "--app.demo.enabled=true",
        "--app.sla.monitor.enabled=true",
        "--server.port=8080");
  }
}
