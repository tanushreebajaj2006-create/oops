package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentStatus;

/** Maps a stored {@link IncidentStatus} back to its behaviour object (used when loading). */
public final class IncidentStates {

  private IncidentStates() {}

  public static IncidentState initial() {
    return NewState.INSTANCE;
  }

  public static IncidentState of(IncidentStatus status) {
    return switch (status) {
      case NEW -> NewState.INSTANCE;
      case CLASSIFIED -> ClassifiedState.INSTANCE;
      case ASSIGNED -> AssignedState.INSTANCE;
      case ACKNOWLEDGED -> AcknowledgedState.INSTANCE;
      case IN_PROGRESS -> InProgressState.INSTANCE;
      case AWAITING_INFO -> AwaitingInfoState.INSTANCE;
      case RESOLVED -> ResolvedState.INSTANCE;
      case VERIFIED -> VerifiedState.INSTANCE;
      case CLOSED -> ClosedState.INSTANCE;
      case CANCELLED -> CancelledState.INSTANCE;
    };
  }
}
