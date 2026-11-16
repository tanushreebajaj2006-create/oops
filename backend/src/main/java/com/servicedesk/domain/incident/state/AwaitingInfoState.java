package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** Technician is waiting for the reporter to answer an information request. */
final class AwaitingInfoState extends AbstractIncidentState {

  static final AwaitingInfoState INSTANCE = new AwaitingInfoState();

  private AwaitingInfoState() {
    super(IncidentStatus.AWAITING_INFO, EnumSet.of(IncidentAction.PROVIDE_INFO));
  }

  @Override
  public IncidentState provideInfo() {
    return InProgressState.INSTANCE;
  }
}
