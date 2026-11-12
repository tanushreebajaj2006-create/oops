package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentStatus;
import java.util.Set;

/** Terminal state: no further actions are possible. */
final class ClosedState extends AbstractIncidentState {

  static final ClosedState INSTANCE = new ClosedState();

  private ClosedState() {
    super(IncidentStatus.CLOSED, Set.of());
  }
}
