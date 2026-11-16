package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.priority.Priority;
import java.time.Instant;

/**
 * The SLA picture of one incident at {@link #evaluatedAt()}: which policy applied and how both
 * clocks are doing. Immutable, so it can be safely handed to the UI, reports and notifications.
 */
public record SlaSnapshot(
    IncidentId incidentId,
    Priority priority,
    String policyName,
    SlaMeasure response,
    SlaMeasure resolution,
    Instant evaluatedAt) {

  public SlaSnapshot {
    Require.notNull(incidentId, "Incident id");
    Require.notNull(priority, "Priority");
    Require.notBlank(policyName, "Policy name");
    Require.notNull(response, "Response measure");
    Require.notNull(resolution, "Resolution measure");
    Require.notNull(evaluatedAt, "Evaluation time");
  }

  /** The worse of the two clocks - what a dashboard should show for the incident. */
  public SlaStatus overall() {
    return SlaStatus.worst(response.status(), resolution.status());
  }

  public boolean isBreached() {
    return overall() == SlaStatus.BREACHED;
  }

  public SlaMeasure measure(SlaKind kind) {
    return kind == SlaKind.RESPONSE ? response : resolution;
  }
}
