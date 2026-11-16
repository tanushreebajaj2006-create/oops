package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentStatus;
import java.util.Set;

/** Terminal state for incidents withdrawn before work started (e.g. duplicates). */
final class CancelledState extends AbstractIncidentState {

  static final CancelledState INSTANCE = new CancelledState();

  private CancelledState() {
    super(IncidentStatus.CANCELLED, Set.of());
  }
}
