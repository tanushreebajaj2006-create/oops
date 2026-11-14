package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.sla.SlaMeasure;
import com.servicedesk.domain.sla.SlaSnapshot;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.Technician;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Predicts which open incidents are likely to miss their resolution SLA <i>before</i> it happens
 * (brief: "SLA Risk Detection"; innovation: "Predictive SLA violation detection").
 *
 * <p>Method:
 *
 * <ol>
 *   <li>Estimate how long this kind of incident usually takes to fix: the <b>median historical
 *       resolution time</b> for its category and priority ({@link IncidentHistory}); with no
 *       history, assume half the SLA allowance.
 *   <li>Adjust for the current situation: nobody assigned (x1.5), assigned but not acknowledged
 *       (x1.2), waiting for the reporter (x1.3), technician at 80 %+ capacity (x1.25).
 *   <li>Remaining work = max(estimate - time already spent, 10 % of estimate). Projected finish =
 *       now + remaining work.
 *   <li>Compare with the deadline: projected total / allowed time {@code >= 1.0} -> HIGH, {@code >=
 *       0.8} -> MEDIUM, otherwise LOW. A clock already AT_RISK is at least MEDIUM.
 * </ol>
 *
 * Incidents whose resolution SLA is already breached or stopped are not predicted - they are no
 * longer a forecast but a fact, reported elsewhere.
 */
public final class SlaRiskPredictor {

  public Optional<SlaRisk> assess(
      Incident incident, SlaSnapshot snapshot, IncidentHistory history, Instant now) {
    Require.notNull(incident, "Incident");
    Require.notNull(snapshot, "SLA snapshot");
    Require.notNull(history, "History");
    SlaMeasure res = snapshot.resolution();
    if (!res.isRunning() || res.status() == SlaStatus.BREACHED) {
      return Optional.empty();
    }
    Duration allowed = Duration.between(res.startedAt(), res.dueAt());
    Duration spent = Duration.between(res.startedAt(), now);
    List<String> reasons = new ArrayList<>();

    String category = incident.category().map(Category::code).orElse("-");
    Optional<Duration> typical = history.typicalResolutionTime(category, snapshot.priority());
    Duration estimate = typical.orElse(allowed.dividedBy(2));
    reasons.add(
        typical.isPresent()
            ? "similar %s %s incidents are usually fixed in %s"
                .formatted(snapshot.priority(), category, human(estimate))
            : "no history yet - assuming half the SLA allowance (%s)".formatted(human(estimate)));

    double factor = 1.0;
    Optional<Technician> assignee = incident.assignee();
    if (assignee.isEmpty()) {
      factor *= 1.5;
      reasons.add("not assigned to anyone yet");
    } else if (incident.acknowledgedAt().isEmpty()) {
      factor *= 1.2;
      reasons.add("assigned but not yet acknowledged");
    }
    if (incident.status() == IncidentStatus.AWAITING_INFO) {
      factor *= 1.3;
      reasons.add("waiting for information from the reporter");
    }
    if (assignee.isPresent() && assignee.get().utilisation() >= 0.8) {
      factor *= 1.25;
      reasons.add(
          "technician %s is at %d%% capacity"
              .formatted(assignee.get().id(), Math.round(assignee.get().utilisation() * 100)));
    }
    Duration adjusted = Duration.ofSeconds(Math.round(estimate.toSeconds() * factor));
    Duration remaining = adjusted.minus(spent);
    Duration floor = adjusted.dividedBy(10);
    if (remaining.compareTo(floor) < 0) {
      remaining = floor;
    }
    Instant projected = now.plus(remaining);
    double ratio =
        (double) Duration.between(res.startedAt(), projected).toSeconds()
            / Math.max(1, allowed.toSeconds());

    SlaRisk.Level level =
        ratio >= 1.0 ? SlaRisk.Level.HIGH : ratio >= 0.8 ? SlaRisk.Level.MEDIUM : SlaRisk.Level.LOW;
    if (res.status() == SlaStatus.AT_RISK && level == SlaRisk.Level.LOW) {
      level = SlaRisk.Level.MEDIUM;
      reasons.add("already past the SLA warning threshold");
    }
    if (level != SlaRisk.Level.LOW) {
      Duration slack = Duration.between(projected, res.dueAt());
      reasons.add(
          slack.isNegative()
              ? "projected to finish %s after the deadline".formatted(human(slack.negated()))
              : "projected to finish only %s before the deadline".formatted(human(slack)));
    }
    return Optional.of(
        new SlaRisk(
            incident, level, Math.round(ratio * 100) / 100.0, projected, res.dueAt(), reasons));
  }

  static String human(Duration d) {
    long minutes = Math.max(0, d.toMinutes());
    if (minutes < 90) {
      return minutes + " min";
    }
    long hours = minutes / 60;
    return hours < 48 ? hours + " h" : (hours / 24) + " days";
  }
}
