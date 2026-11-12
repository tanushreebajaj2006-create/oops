package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** Technician accepted the incident (SLA response target met here). */
final class AcknowledgedState extends AbstractIncidentState {

  static final AcknowledgedState INSTANCE = new AcknowledgedState();

  private AcknowledgedState() {
    super(
        IncidentStatus.ACKNOWLEDGED, EnumSet.of(IncidentAction.ASSIGN, IncidentAction.START_WORK));
  }

  @Override
  public IncidentState assign() {
    return AssignedState.INSTANCE;
  }

  @Override
  public IncidentState startWork() {
    return InProgressState.INSTANCE;
  }
}
