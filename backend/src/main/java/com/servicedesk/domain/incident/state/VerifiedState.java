package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** Reporter confirmed the fix. Ready to be closed. */
final class VerifiedState extends AbstractIncidentState {

  static final VerifiedState INSTANCE = new VerifiedState();

  private VerifiedState() {
    super(IncidentStatus.VERIFIED, EnumSet.of(IncidentAction.CLOSE));
  }

  @Override
  public IncidentState close() {
    return ClosedState.INSTANCE;
  }
}
