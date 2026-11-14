package com.servicedesk.domain.sla;

import static com.servicedesk.domain.support.TestData.minutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.InvalidIncidentException;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.SlaConfigurationMissingException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.support.TestData;
import com.servicedesk.domain.user.Technician;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SLA calculation and monitoring")
class SlaTest {

  private final TestData data = new TestData();
  private final SlaPolicyCatalog catalog = SlaPolicyCatalog.standard();
  private final SlaCalculator calculator = new SlaCalculator(catalog, data.clock);

  @Test
  @DisplayName("TC-44 deadlines follow the brief's table (P1: respond 5 min, resolve 30 min)")
  void standardDeadlines() {
    Incident p1 = data.classified(data.network, Priority.P1);
    SlaSnapshot s = calculator.evaluate(p1);
    assertThat(s.policyName()).isEqualTo("Standard SLA");
    assertThat(s.response().dueAt()).isEqualTo(minutes(5));
    assertThat(s.resolution().dueAt()).isEqualTo(minutes(30));

    Incident p4 = data.classified(data.network, Priority.P4);
    assertThat(calculator.dueAt(p4, SlaKind.RESPONSE)).isEqualTo(minutes(4 * 60));
    assertThat(calculator.dueAt(p4, SlaKind.RESOLUTION)).isEqualTo(minutes(24 * 60));
  }

  @Test
  @DisplayName("TC-45 running clock goes ON_TRACK -> AT_RISK at 75% -> BREACHED after the deadline")
  void statusProgression() {
    Incident i = data.classified(data.network, Priority.P1);

    data.clock.set(minutes(3)); // 60% of 5 min
    assertThat(calculator.evaluate(i).response().status()).isEqualTo(SlaStatus.ON_TRACK);

    data.clock.set(minutes(4)); // 80%
    SlaSnapshot atRisk = calculator.evaluate(i);
    assertThat(atRisk.response().status()).isEqualTo(SlaStatus.AT_RISK);
    assertThat(atRisk.response().remaining(data.clock.instant())).isEqualTo(Duration.ofMinutes(1));
    assertThat(atRisk.overall()).isEqualTo(SlaStatus.AT_RISK);

    data.clock.set(minutes(5)); // exactly at the deadline: not yet breached
    assertThat(calculator.evaluate(i).response().status()).isEqualTo(SlaStatus.AT_RISK);

    data.clock.set(minutes(5).plusSeconds(1));
    SlaSnapshot late = calculator.evaluate(i);
    assertThat(late.response().status()).isEqualTo(SlaStatus.BREACHED);
    assertThat(late.isBreached()).isTrue();
    assertThat(late.resolution().status()).isEqualTo(SlaStatus.ON_TRACK); // 5/30 min used
  }

  @Test
  @DisplayName(
      "TC-46 acknowledging exactly at the deadline meets the response SLA; one second late"
          + " breaches")
  void responseBoundary() {
    Technician t = TestData.tech("T1", 5, 3);
    Incident onTime = data.classified(data.network, Priority.P1);
    onTime.assignTo(t, data.system, minutes(1));
    onTime.acknowledge(t, minutes(5));

    Incident late = data.classified(data.network, Priority.P1);
    late.assignTo(t, data.system, minutes(1));
    late.acknowledge(t, minutes(5).plusSeconds(1));

    data.clock.set(minutes(10));
    assertThat(calculator.evaluate(onTime).response().status()).isEqualTo(SlaStatus.MET);
    SlaMeasure lateResponse = calculator.evaluate(late).response();
    assertThat(lateResponse.status()).isEqualTo(SlaStatus.BREACHED);
    // a finished clock does not keep ageing
    assertThat(lateResponse.overdue(minutes(500))).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  @DisplayName(
      "TC-47 a service-specific policy overrides the default; missing priorities fall back")
  void servicePolicyOverride() {
    catalog.register(
        SlaPolicy.forService("Payments Gold", "payment")
            .setTarget(Priority.P1, SlaTarget.of(Duration.ofMinutes(2), Duration.ofMinutes(15))));
    Incident p1 = data.classified(data.network, Priority.P1);
    SlaSnapshot s = calculator.evaluate(p1);
    assertThat(s.policyName()).isEqualTo("Payments Gold");
    assertThat(s.response().dueAt()).isEqualTo(minutes(2));
    assertThat(s.resolution().dueAt()).isEqualTo(minutes(15));
    // P3 is not defined by the gold policy -> standard 8 h
    Incident p3 = data.classified(data.network, Priority.P3);
    assertThat(calculator.dueAt(p3, SlaKind.RESOLUTION)).isEqualTo(minutes(8 * 60));
  }

  @Test
  @DisplayName(
      "TC-48 missing SLA configuration and unprioritised incidents are reported, not guessed")
  void missingConfiguration() {
    SlaCalculator noDefault = new SlaCalculator(new SlaPolicyCatalog(), data.clock);
    Incident i = data.classified(data.network, Priority.P2);
    assertThatThrownBy(() -> noDefault.evaluate(i))
        .isInstanceOf(SlaConfigurationMissingException.class)
        .hasMessageContaining("PAYMENT");

    SlaCalculator partial =
        new SlaCalculator(
            new SlaPolicyCatalog(
                SlaPolicy.defaultPolicy("Only P1")
                    .setTarget(
                        Priority.P1, SlaTarget.of(Duration.ofMinutes(5), Duration.ofMinutes(30)))),
            data.clock);
    assertThatThrownBy(() -> partial.evaluate(i))
        .isInstanceOf(SlaConfigurationMissingException.class)
        .hasMessageContaining("P2");

    Incident unprioritised = data.reported(data.payment);
    assertThatThrownBy(() -> calculator.evaluate(unprioritised))
        .isInstanceOf(InvalidIncidentException.class);
  }

  @Test
  @DisplayName("TC-49 SLA policy rejects inconsistent targets and thresholds")
  void policyValidation() {
    assertThatThrownBy(() -> SlaTarget.of(Duration.ofHours(2), Duration.ofHours(1)))
        .isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> SlaTarget.of(Duration.ZERO, Duration.ofHours(1)))
        .isInstanceOf(InvalidInputException.class);
    SlaPolicy policy = SlaPolicy.standard();
    // P1 may not be given more time than P2 (2 h)
    assertThatThrownBy(
            () ->
                policy.setTarget(
                    Priority.P1, SlaTarget.of(Duration.ofMinutes(5), Duration.ofHours(3))))
        .isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> policy.setWarningThreshold(0.99))
        .isInstanceOf(InvalidInputException.class);
    assertThat(policy.targets()).hasSize(4);
  }

  @Test
  @DisplayName(
      "TC-50 monitor raises each warning and violation exactly once, then records the violation")
  void monitorRaisesOnce() {
    InMemorySlaAlertLog log = new InMemorySlaAlertLog();
    SlaMonitor monitor = new SlaMonitor(calculator, log);
    Incident i = data.classified(data.network, Priority.P1);
    Technician t = TestData.tech("T1", 5, 3);
    i.assignTo(t, data.system, minutes(1));

    data.clock.set(minutes(2));
    assertThat(monitor.scan(List.of(i)).newAlerts()).isEmpty();

    data.clock.set(minutes(4));
    SlaScanResult warn = monitor.scan(List.of(i));
    assertThat(warn.warnings())
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.kind()).isEqualTo(SlaKind.RESPONSE);
              assertThat(a.assigneeId()).contains(t.id());
            });
    assertThat(monitor.scan(List.of(i)).newAlerts()).isEmpty(); // no repeat

    data.clock.set(minutes(7));
    SlaScanResult breach = monitor.scan(List.of(i));
    assertThat(breach.violations())
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.overdueBy()).isEqualTo(Duration.ofMinutes(2));
              assertThat(a.describe()).contains("RESPONSE SLA violated");
            });
    assertThat(monitor.scan(List.of(i)).newAlerts()).isEmpty();
    assertThat(log.violations()).hasSize(1);
    assertThat(log.forIncident(i.id())).hasSize(2);
  }

  @Test
  @DisplayName("TC-51 one misconfigured incident does not stop the SLA scan of the others")
  void monitorSkipsBadIncidents() {
    SlaPolicyCatalog onlyPayment = new SlaPolicyCatalog();
    onlyPayment.register(
        SlaPolicy.forService("Payments", "PAYMENT")
            .setTarget(Priority.P1, SlaTarget.of(Duration.ofMinutes(5), Duration.ofMinutes(30))));
    SlaMonitor monitor =
        new SlaMonitor(new SlaCalculator(onlyPayment, data.clock), new InMemorySlaAlertLog());

    Incident good = data.classified(data.network, Priority.P1);
    Incident bad = data.reported(data.wiki);
    bad.classify(data.network, data.system, TestData.T0);
    bad.applyPriority(Priority.P1, data.system, TestData.T0);
    Incident unprioritised = data.reported(data.payment);

    data.clock.set(minutes(40));
    SlaScanResult r = monitor.scan(List.of(bad, unprioritised, good));
    assertThat(r.skipped()).containsOnlyKeys(bad.id());
    assertThat(r.violations()).extracting(SlaAlert::incidentId).containsOnly(good.id());
    assertThat(r.violations()).hasSize(2); // response and resolution both missed
  }

  @Test
  @DisplayName("TC-52 cancelled incident's unfinished clocks are NOT_APPLICABLE, not breaches")
  void cancelledNotApplicable() {
    Incident i = data.classified(data.network, Priority.P1);
    i.cancel(data.employee, "Reported by mistake", minutes(1));
    data.clock.set(minutes(60));
    SlaSnapshot s = calculator.evaluate(i);
    assertThat(s.response().status()).isEqualTo(SlaStatus.NOT_APPLICABLE);
    assertThat(s.overall()).isEqualTo(SlaStatus.NOT_APPLICABLE);
  }
}
