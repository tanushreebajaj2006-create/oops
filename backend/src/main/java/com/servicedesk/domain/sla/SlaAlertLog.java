package com.servicedesk.domain.sla;

import com.servicedesk.domain.incident.IncidentId;
import java.util.List;

/**
 * Where raised SLA alerts are remembered, so that each warning and each violation is reported
 * exactly once even though the monitor re-checks every incident every minute.
 *
 * <p>An interface (<b>Repository</b>-style contract): the domain decides <i>what</i> must be
 * stored; Phase 4 provides a database implementation, tests use {@link InMemorySlaAlertLog}.
 */
public interface SlaAlertLog {

  boolean hasRaised(IncidentId incidentId, SlaKind kind, SlaAlert.Type type);

  void record(SlaAlert alert);

  /** Every violation ever recorded, oldest first. */
  List<SlaAlert> violations();

  /** All alerts (warnings and violations) for one incident, oldest first. */
  List<SlaAlert> forIncident(IncidentId incidentId);
}
