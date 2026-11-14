package com.servicedesk.domain.audit;

import static com.servicedesk.domain.support.TestData.minutes;
import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.event.IncidentEventListener;
import com.servicedesk.domain.incident.event.IncidentEventPublisher;
import com.servicedesk.domain.priority.MatrixPriorityStrategy;
import com.servicedesk.domain.support.TestData;
import com.servicedesk.domain.user.Administrator;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Audit trail and event publishing")
class AuditTrailTest {

  private final TestData data = new TestData();
  private final InMemoryAuditLog log = new InMemoryAuditLog();
  private final IncidentEventPublisher publisher =
      new IncidentEventPublisher().subscribe(new AuditTrailListener(log));
  private final Technician tech = TestData.tech("T205", 5, 4, TestData.skill("NETWORK", 4));

  /** Runs the brief's example timeline and publishes each step's events, as the app layer will. */
  private Incident runScenario() {
    Incident i = data.reported(data.payment); // 10:00
    publisher.publishAll(i.pullEvents());
    i.classify(data.network, data.system, minutes(1));
    i.prioritize(new MatrixPriorityStrategy(), data.system, minutes(1));
    i.assignTo(tech, data.system, minutes(2));
    i.acknowledge(tech, minutes(7));
    i.startWork(tech, minutes(15));
    i.resolve(tech, "Restarted payment pods", "Memory leak", minutes(20));
    i.verify(data.employee, minutes(30));
    i.close(data.employee, null, minutes(35));
    publisher.publishAll(i.pullEvents());
    return i;
  }

  @Test
  @DisplayName("TC-64 the audit log holds the complete incident history in the brief's format")
  void completeHistory() {
    Incident i = runScenario();
    List<AuditEntry> history = log.historyOf(i.id());
    assertThat(history).hasSize(9);
    List<String> lines = history.stream().map(e -> e.format(ZoneOffset.UTC)).toList();
    assertThat(lines.get(0))
        .isEqualTo("10:00 - INC2000 created by EMPLOYEE E102 on service PAYMENT");
    assertThat(lines.get(1))
        .isEqualTo("10:01 - INC2000 - Incident classified as NETWORK [NEW -> CLASSIFIED]");
    assertThat(lines.get(2)).startsWith("10:01 - INC2000 - Priority calculated as P1");
    assertThat(lines.get(3))
        .startsWith("10:02 - INC2000 - Assigned to Technician T205")
        .endsWith("[CLASSIFIED -> ASSIGNED]");
    assertThat(lines.get(4))
        .isEqualTo("10:07 - INC2000 - Technician acknowledged incident [ASSIGNED -> ACKNOWLEDGED]");
    assertThat(lines.get(5))
        .isEqualTo("10:15 - INC2000 - Investigation started [ACKNOWLEDGED -> IN_PROGRESS]");
    assertThat(lines.get(6))
        .isEqualTo(
            "10:20 - INC2000 - Incident marked RESOLVED: Restarted payment pods [IN_PROGRESS ->"
                + " RESOLVED]");
    assertThat(lines.get(8)).isEqualTo("10:35 - INC2000 - Incident CLOSED [VERIFIED -> CLOSED]");

    AuditEntry assigned = history.get(3);
    assertThat(assigned.actorId()).isEqualTo(data.system.id());
    assertThat(assigned.action()).isEqualTo("ASSIGNED");
    assertThat(assigned.previousStatus()).isEqualTo(IncidentStatus.CLASSIFIED);
    assertThat(assigned.newStatus()).isEqualTo(IncidentStatus.ASSIGNED);
    assertThat(history).extracting(AuditEntry::sequence).isSorted().doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("TC-65 audit search filters by actor, action and time window")
  void search() {
    runScenario();
    assertThat(log.search(AuditQuery.all().byActor(tech.id())))
        .extracting(AuditEntry::action)
        .containsExactly("STATUS_CHANGED", "STATUS_CHANGED", "RESOLVED");
    assertThat(log.search(AuditQuery.all().withAction("closed"))).hasSize(1);
    assertThat(log.search(AuditQuery.all().between(minutes(5), minutes(20)))).hasSize(2);
    assertThat(log.search(AuditQuery.all().byActor(new UserId("NOBODY")))).isEmpty();
  }

  @Test
  @DisplayName("TC-66 administrative actions are audited without an incident")
  void adminAction() {
    Administrator admin = new Administrator(new UserId("A001"), "Admin", "admin@corp.com", "h");
    AuditEntry e =
        log.append(
            AuditEntry.adminAction(
                admin,
                "strategy_changed",
                "Assignment strategy SKILL_BASED -> LOAD_BASED",
                TestData.T0));
    assertThat(e.sequence()).isEqualTo(1);
    assertThat(e.incident()).isEmpty();
    assertThat(e.action()).isEqualTo("STRATEGY_CHANGED");
    assertThat(e.format(ZoneOffset.UTC))
        .isEqualTo("10:00 - Assignment strategy SKILL_BASED -> LOAD_BASED");
  }

  @Test
  @DisplayName("TC-67 a failing listener does not stop the others or the business action")
  void listenerFaultIsolation() {
    List<String> errors = new ArrayList<>();
    List<String> seen = new ArrayList<>();
    IncidentEventListener broken =
        e -> {
          throw new IllegalStateException("mail server down");
        };
    IncidentEventPublisher p =
        new IncidentEventPublisher((l, ex) -> errors.add(ex.getMessage()))
            .subscribe(broken)
            .subscribe(e -> seen.add(e.type().name()))
            .subscribe(new AuditTrailListener(log));
    p.subscribe(broken); // duplicate subscription is ignored
    assertThat(p.listenerCount()).isEqualTo(3);

    Incident i = data.reported(data.payment);
    int failures = p.publishAll(i.pullEvents());
    assertThat(failures).isEqualTo(1);
    assertThat(errors).containsExactly("mail server down");
    assertThat(seen).containsExactly("CREATED");
    assertThat(log.size()).isEqualTo(1);
  }
}
