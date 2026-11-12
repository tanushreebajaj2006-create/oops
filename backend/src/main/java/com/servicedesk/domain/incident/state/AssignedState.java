package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** A technician has been chosen but has not yet acknowledged. Can be re-assigned. */
final class AssignedState extends AbstractIncidentState {

  static final AssignedState INSTANCE = new AssignedState();

  private AssignedState() {
    super(
        IncidentStatus.ASSIGNED,
        EnumSet.of(IncidentAction.ASSIGN, IncidentAction.ACKNOWLEDGE, IncidentAction.CANCEL));
  }

  @Override
  public IncidentState assign() {
    return AssignedState.INSTANCE;
  }

  @Override
  public IncidentState acknowledge() {
    return AcknowledgedState.INSTANCE;
  }

  @Override
  public IncidentState cancel() {
    return CancelledState.INSTANCE;
  }
}
