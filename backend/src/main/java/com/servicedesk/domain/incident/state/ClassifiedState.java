package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** Category and priority known; waiting for a technician. Can be re-classified. */
final class ClassifiedState extends AbstractIncidentState {

  static final ClassifiedState INSTANCE = new ClassifiedState();

  private ClassifiedState() {
    super(
        IncidentStatus.CLASSIFIED,
        EnumSet.of(IncidentAction.CLASSIFY, IncidentAction.ASSIGN, IncidentAction.CANCEL));
  }

  @Override
  public IncidentState classify() {
    return ClassifiedState.INSTANCE;
  }

  @Override
  public IncidentState assign() {
    return AssignedState.INSTANCE;
  }

  @Override
  public IncidentState cancel() {
    return CancelledState.INSTANCE;
  }
}
