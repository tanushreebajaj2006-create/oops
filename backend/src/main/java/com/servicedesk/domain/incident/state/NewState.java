package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** Just reported. Must be classified (category + priority) before anything else. */
final class NewState extends AbstractIncidentState {

  static final NewState INSTANCE = new NewState();

  private NewState() {
    super(IncidentStatus.NEW, EnumSet.of(IncidentAction.CLASSIFY, IncidentAction.CANCEL));
  }

  @Override
  public IncidentState classify() {
    return ClassifiedState.INSTANCE;
  }

  @Override
  public IncidentState cancel() {
    return CancelledState.INSTANCE;
  }
}
