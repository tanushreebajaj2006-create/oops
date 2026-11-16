package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** Technician is investigating. Re-assigning (e.g. escalation) sends it back to ASSIGNED. */
final class InProgressState extends AbstractIncidentState {

  static final InProgressState INSTANCE = new InProgressState();

  private InProgressState() {
    super(
        IncidentStatus.IN_PROGRESS,
        EnumSet.of(IncidentAction.ASSIGN, IncidentAction.REQUEST_INFO, IncidentAction.RESOLVE));
  }

  @Override
  public IncidentState assign() {
    return AssignedState.INSTANCE;
  }

  @Override
  public IncidentState requestInfo() {
    return AwaitingInfoState.INSTANCE;
  }

  @Override
  public IncidentState resolve() {
    return ResolvedState.INSTANCE;
  }
}
