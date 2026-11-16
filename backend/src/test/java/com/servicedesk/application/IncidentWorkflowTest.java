package com.servicedesk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.application.port.SettingsStore;
import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.audit.AuditQuery;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.incident.event.EventType;
import com.servicedesk.domain.notification.InAppInbox;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.support.MutableClock;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end use cases through the application services with the real database, Spring wiring,
 * strategies, audit and notifications. Time is controlled with a {@link MutableClock}. Covers the
 * brief's three demonstration workflows.
 */
@SpringBootTest
@Transactional
@Import(IncidentWorkflowTest.TestClock.class)
@DisplayName("Incident workflows (application services)")
class IncidentWorkflowTest {

  static final Instant T0 = Instant.parse("2026-11-12T10:00:00Z");

  @TestConfiguration
  static class TestClock {
    @Bean
    @Primary
    MutableClock mutableClock() {
      return new MutableClock(T0);
    }
  }

  @Autowired MutableClock clock;
  @Autowired IncidentService incidents;
  @Autowired AdminService admin;
  @Autowired ReportService reports;
  @Autowired SlaMonitoringService slaMonitoring;
  @Autowired InAppInbox inbox;
  @Autowired UserRepository users;
  @Autowired SettingsStore settings;

  static final UserId ASHA = new UserId("E101"); // employee
  static final UserId RAVI = new UserId("E102"); // another employee
  static final UserId NEHA = new UserId("T201"); // APPLICATION 5
  static final UserId ARJUN = new UserId("T202"); // NETWORK 4
  static final UserId MEERA = new UserId("L301"); // team lead, APPLICATION 4
  static final UserId ADMIN = new UserId("A001");

  @BeforeEach
  void resetClock() {
    clock.set(T0);
  }

  @AfterEach
  void resetStrategies() {
    admin.changeStrategies(ADMIN, "SKILL_BASED", "MATRIX");
  }

  private Incident reportPaymentOutage(UserId reporter) {
    return incidents.report(
        reporter,
        new IncidentService.ReportCommand(
            "Payment application unavailable",
            "Users are unable to complete transactions since 10:00",
            "PAYMENT",
            Level.CRITICAL,
            Level.HIGH,
            300,
            "APPLICATION"));
  }

  private void tick(Duration d) {
    clock.advance(d);
  }

  @Test
  @DisplayName(
      "TC-86 Workflow 1: employee reports -> system prioritises -> technician assigned ->"
          + " notifications + audit")
  void workflow1ReportPrioritiseAssignNotify() {
    Incident i = reportPaymentOutage(ASHA);

    assertThat(i.status()).isEqualTo(IncidentStatus.ASSIGNED);
    assertThat(i.priority()).contains(Priority.P1); // CRITICAL x HIGH x CRITICAL = 48
    assertThat(i.assignee()).map(Technician::id).contains(NEHA); // best APPLICATION skill
    assertThat(incidents.slaOf(i))
        .get()
        .satisfies(
            s -> {
              assertThat(s.policyName()).isEqualTo("Payments Gold");
              assertThat(s.response().dueAt()).isEqualTo(T0.plus(Duration.ofMinutes(2)));
            });

    assertThat(inbox.itemsFor(NEHA))
        .extracting(x -> x.notification().eventType())
        .contains(EventType.ASSIGNED);
    assertThat(inbox.itemsFor(ASHA))
        .extracting(x -> x.notification().eventType())
        .contains(EventType.CREATED, EventType.ASSIGNED);
    assertThat(incidents.history(ASHA, i.id()))
        .extracting(AuditEntry::action)
        .containsExactly("CREATED", "CLASSIFIED", "PRIORITY_CHANGED", "ASSIGNED");
    assertThat(incidents.history(ASHA, i.id()).get(3).description())
        .contains("SKILL_BASED")
        .contains("APPLICATION skill 5/5");
  }

  @Test
  @DisplayName(
      "TC-87 Workflow 2: technician investigates -> resolves -> reporter notified, verifies, rates"
          + " and closes")
  void workflow2InvestigateResolveClose() {
    Incident i = reportPaymentOutage(ASHA);
    tick(Duration.ofMinutes(1));
    incidents.acknowledge(NEHA, i.id());
    incidents.startWork(NEHA, i.id());
    incidents.addNote(NEHA, i.id(), NoteType.INVESTIGATION, "Pods OOM-killed");
    incidents.requestInfo(NEHA, i.id(), "Which browser shows the error?");
    assertThat(incidents.get(ASHA, i.id()).status()).isEqualTo(IncidentStatus.AWAITING_INFO);
    incidents.provideInfo(ASHA, i.id(), "Chrome and Edge both");
    assertThat(((Technician) users.get(NEHA)).activeWorkload()).isEqualTo(1);

    tick(Duration.ofMinutes(10));
    incidents.resolve(NEHA, i.id(), "Raised memory limit of payment pods", "Memory leak");
    assertThat(((Technician) users.get(NEHA)).activeWorkload()).isZero();
    assertThat(inbox.itemsFor(ASHA).get(0).notification().subject()).contains("has been resolved");
    assertThat(incidents.slaOf(incidents.get(ASHA, i.id())))
        .get()
        .extracting(s -> s.resolution().status())
        .isEqualTo(SlaStatus.MET);

    incidents.verify(ASHA, i.id());
    incidents.giveFeedback(ASHA, i.id(), 5, "Fast fix");
    Incident closed = incidents.close(ASHA, i.id(), "Works now");
    assertThat(closed.status()).isEqualTo(IncidentStatus.CLOSED);
    // the reporter does not see internal investigation notes, the technician does
    assertThat(closed.notesVisibleTo(users.get(ASHA))).noneMatch(n -> n.text().contains("OOM"));
    assertThat(incidents.history(NEHA, i.id()))
        .extracting(AuditEntry::action)
        .contains("NOTE_ADDED", "RESOLVED", "FEEDBACK_GIVEN", "CLOSED");
  }

  @Test
  @DisplayName(
      "TC-88 visibility and authorisation: employees see their own, technicians their queue;"
          + " illegal actions are refused")
  void visibilityAndAuthorisation() {
    Incident i = reportPaymentOutage(ASHA);
    assertThatThrownBy(() -> incidents.get(RAVI, i.id()))
        .isInstanceOf(UnauthorizedOperationException.class);
    assertThat(incidents.search(RAVI, IncidentQuery.all(), null).items()).isEmpty();
    assertThat(incidents.search(ASHA, IncidentQuery.all(), null).items())
        .extracting(Incident::id)
        .contains(i.id());
    assertThat(incidents.search(NEHA, IncidentQuery.all(), null).items())
        .extracting(Incident::id)
        .containsExactly(i.id());
    assertThat(incidents.search(ARJUN, IncidentQuery.all(), null).items()).isEmpty();
    assertThat(incidents.search(MEERA, IncidentQuery.all(), null).total())
        .isGreaterThanOrEqualTo(1);

    assertThatThrownBy(() -> incidents.assign(ASHA, i.id(), ARJUN))
        .isInstanceOf(UnauthorizedOperationException.class);
    assertThatThrownBy(() -> incidents.acknowledge(ARJUN, i.id()))
        .isInstanceOf(UnauthorizedOperationException.class);
    assertThatThrownBy(() -> incidents.close(ASHA, i.id(), "skip the process"))
        .isInstanceOf(InvalidStateTransitionException.class);
    assertThatThrownBy(() -> incidents.recommend(ASHA, i.id()))
        .isInstanceOf(UnauthorizedOperationException.class);
    assertThat(incidents.recommend(MEERA, i.id())).isNotEmpty();
  }

  @Test
  @DisplayName(
      "TC-89 updating impact recalculates priority; only the reporter or a supervisor may edit")
  void updateDetailsReprioritises() {
    Incident i =
        incidents.report(
            ASHA,
            new IncidentService.ReportCommand(
                "Wiki search broken",
                "Search on the wiki returns nothing at all",
                "WIKI",
                Level.LOW,
                Level.LOW,
                2,
                "SOFTWARE"));
    assertThat(i.priority()).contains(Priority.P4);
    Incident updated =
        incidents.updateDetails(
            ASHA,
            i.id(),
            new IncidentService.DetailsCommand(
                "Wiki down for everyone",
                "The wiki is down for the whole company now",
                Level.CRITICAL,
                Level.CRITICAL,
                900));
    // CRITICAL(4) x CRITICAL(4) x WIKI criticality LOW(1) = 16 -> P3 (was 1 -> P4)
    assertThat(updated.priority()).contains(Priority.P3);
    assertThat(incidents.history(ASHA, i.id()))
        .extracting(AuditEntry::action)
        .contains("DETAILS_UPDATED");
    assertThatThrownBy(
            () ->
                incidents.updateDetails(
                    RAVI,
                    i.id(),
                    new IncidentService.DetailsCommand(
                        "Hijacked title", "Someone else editing this", Level.LOW, Level.LOW, 1)))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName("TC-90 admin switches the assignment strategy at runtime; it is stored and audited")
  void switchStrategy() {
    admin.changeStrategies(ADMIN, "load_based", null);
    assertThat(settings.get(SettingsStore.ASSIGNMENT_STRATEGY)).contains("LOAD_BASED");
    assertThat(admin.searchAudit(ADMIN, AuditQuery.all().withAction("STRATEGY_CHANGED")))
        .extracting(AuditEntry::description)
        .contains("Assignment strategy SKILL_BASED -> LOAD_BASED");

    // load-based ignores skills: the payment incident goes to the least-loaded technician by id,
    // not to the APPLICATION expert
    Incident i = reportPaymentOutage(ASHA);
    assertThat(i.assignee()).isPresent();
    assertThat(incidents.history(ASHA, i.id()).get(3).description()).contains("LOAD_BASED");

    assertThatThrownBy(() -> admin.changeStrategies(ADMIN, "MAGIC", null))
        .isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> admin.changeStrategies(NEHA, "ROUND_ROBIN", null))
        .isInstanceOf(UnauthorizedOperationException.class);
  }

  @Test
  @DisplayName(
      "TC-91 Workflow 3a: SLA warning, violation, then escalation Technician -> Team Lead ->"
          + " Administrator")
  void slaMonitoringAndEscalation() {
    Incident i = reportPaymentOutage(ASHA); // P1 on PAYMENT: respond within 2 min

    tick(Duration.ofSeconds(90)); // 75% of 2 min used (warning threshold 70%)
    var warn = slaMonitoring.runCheck();
    assertThat(warn.warnings()).isEqualTo(1);
    assertThat(inbox.itemsFor(NEHA).get(0).notification().eventType())
        .isEqualTo(EventType.SLA_WARNING);

    tick(Duration.ofSeconds(90)); // 3 min: response deadline missed
    var breach = slaMonitoring.runCheck();
    assertThat(breach.violations()).isEqualTo(1);
    assertThat(breach.escalations()).isEqualTo(1);
    Incident afterL1 = incidents.get(MEERA, i.id());
    assertThat(afterL1.escalationLevel()).isEqualTo(1);
    assertThat(afterL1.assignee()).map(Technician::id).contains(MEERA); // reassigned to the lead
    assertThat(inbox.itemsFor(MEERA))
        .extracting(x -> x.notification().eventType())
        .contains(EventType.ESCALATED, EventType.ASSIGNED);

    tick(Duration.ofMinutes(2)); // still nobody acknowledged: overdue by a full response period
    var second = slaMonitoring.runCheck();
    assertThat(second.escalations()).isEqualTo(1);
    assertThat(second.violations()).isZero(); // already recorded once
    assertThat(incidents.get(ADMIN, i.id()).escalationLevel()).isEqualTo(2);
    assertThat(inbox.itemsFor(ADMIN))
        .extracting(x -> x.notification().subject())
        .anyMatch(s -> s.contains("escalated to you (level 2)"));

    assertThat(slaMonitoring.runCheck().escalations()).isZero(); // nothing more to do
    assertThat(incidents.history(ADMIN, i.id()))
        .extracting(AuditEntry::action)
        .contains("SLA_WARNING", "SLA_VIOLATION", "ESCALATED");
  }

  @Test
  @DisplayName("TC-92 search by SLA status and dashboard reports reflect the current state")
  void slaSearchAndReports() {
    Incident late = reportPaymentOutage(ASHA);
    tick(Duration.ofMinutes(5));
    Incident fresh =
        incidents.report(
            RAVI,
            new IncidentService.ReportCommand(
                "VPN slow",
                "VPN throughput dropped to 1 Mbit since this morning",
                "VPN",
                Level.MEDIUM,
                Level.MEDIUM,
                10,
                "NETWORK"));

    assertThat(incidents.search(MEERA, IncidentQuery.all(), SlaStatus.BREACHED).items())
        .extracting(Incident::id)
        .contains(late.id())
        .doesNotContain(fresh.id());
    assertThat(incidents.search(MEERA, IncidentQuery.all(), SlaStatus.ON_TRACK).items())
        .extracting(Incident::id)
        .contains(fresh.id());

    var overview = reports.overview(ADMIN);
    assertThat(overview.open()).isGreaterThanOrEqualTo(2);
    assertThat(overview.criticalOpen()).isGreaterThanOrEqualTo(1);
    assertThat(reports.byService(ADMIN))
        .filteredOn(b -> b.code().equals("PAYMENT"))
        .singleElement()
        .satisfies(b -> assertThat(b.open()).isGreaterThanOrEqualTo(1));
    // the VPN (NETWORK) incident went to the best network specialist, team lead L301 (skill 5)
    assertThat(fresh.assignee()).map(Technician::id).contains(MEERA);
    assertThat(reports.technicianWorkload(ADMIN))
        .filteredOn(l -> l.technician().id().equals(MEERA))
        .singleElement()
        .extracting(ReportService.TechnicianLoad::activeIncidents)
        .isEqualTo(1);
    assertThatThrownBy(() -> reports.overview(ASHA))
        .isInstanceOf(UnauthorizedOperationException.class);
  }
}
