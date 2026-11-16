package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.InvalidIncidentException;
import com.servicedesk.domain.exception.SlaConfigurationMissingException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Works out the SLA deadlines and current status of an incident.
 *
 * <p>Rules (documented assumptions):
 *
 * <ul>
 *   <li>Both clocks start when the incident is created and run 24x7 (no business-hours calendar).
 *   <li>The response clock stops at the first acknowledgement by a technician.
 *   <li>The resolution clock stops when the incident is RESOLVED. If the reporter reopens it the
 *       clock resumes from the original start - the customer's wait did not reset.
 *   <li>Deadlines use the incident's <i>current</i> priority, so raising the priority tightens the
 *       deadline immediately.
 *   <li>A cancelled incident's unfinished clocks become NOT_APPLICABLE.
 * </ul>
 *
 * The current time comes from an injected {@link Clock}, so tests can move time precisely.
 */
public class SlaCalculator {

  private final SlaPolicyCatalog catalog;
  private final Clock clock;

  public SlaCalculator(SlaPolicyCatalog catalog, Clock clock) {
    this.catalog = Require.notNull(catalog, "SLA policy catalog");
    this.clock = Require.notNull(clock, "Clock");
  }

  /**
   * @throws InvalidIncidentException if the incident has no priority yet (SLA cannot be known)
   * @throws SlaConfigurationMissingException if no policy covers the incident
   */
  public SlaSnapshot evaluate(Incident incident) {
    Require.notNull(incident, "Incident");
    Priority priority =
        incident
            .priority()
            .orElseThrow(
                () ->
                    new InvalidIncidentException(
                        incident.id() + " has no priority yet, so its SLA is unknown"));
    SlaPolicy policy = catalog.policyFor(incident.service());
    SlaTarget target = catalog.targetFor(incident.service(), priority);
    Instant now = clock.instant();
    boolean cancelled = incident.status() == IncidentStatus.CANCELLED;

    SlaMeasure response =
        measure(
            SlaKind.RESPONSE,
            incident.createdAt(),
            target.response(),
            incident.acknowledgedAt().orElse(null),
            cancelled,
            policy.warningThreshold(),
            now);
    SlaMeasure resolution =
        measure(
            SlaKind.RESOLUTION,
            incident.createdAt(),
            target.resolution(),
            incident.resolvedAt().orElse(null),
            cancelled,
            policy.warningThreshold(),
            now);
    return new SlaSnapshot(incident.id(), priority, policy.name(), response, resolution, now);
  }

  /** Deadlines only, without status - handy when showing a freshly created incident. */
  public Instant dueAt(Incident incident, SlaKind kind) {
    return evaluate(incident).measure(kind).dueAt();
  }

  static SlaMeasure measure(
      SlaKind kind,
      Instant start,
      Duration allowed,
      Instant completedAt,
      boolean cancelled,
      double warningThreshold,
      Instant now) {
    Instant due = start.plus(allowed);
    Instant reference = completedAt != null ? completedAt : now;
    double elapsed =
        (double) Duration.between(start, reference).toMillis() / Math.max(1, allowed.toMillis());

    SlaStatus status;
    if (completedAt != null) {
      status = completedAt.isAfter(due) ? SlaStatus.BREACHED : SlaStatus.MET;
    } else if (cancelled) {
      status = SlaStatus.NOT_APPLICABLE;
    } else if (now.isAfter(due)) {
      status = SlaStatus.BREACHED;
    } else if (elapsed >= warningThreshold) {
      status = SlaStatus.AT_RISK;
    } else {
      status = SlaStatus.ON_TRACK;
    }
    return new SlaMeasure(kind, start, due, completedAt, status, Math.max(0, elapsed));
  }
}
