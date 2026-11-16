package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.UserId;
import java.util.Optional;

/** A technician was given the incident (first assignment or re-assignment). */
public final class IncidentAssigned extends IncidentEvent {

  private final UserId technicianId;
  private final UserId previousTechnicianId;
  private final String reason;

  public IncidentAssigned(
      EventHeader header, UserId technicianId, UserId previousTechnicianId, String reason) {
    super(header);
    this.technicianId = Require.notNull(technicianId, "Technician");
    this.previousTechnicianId = previousTechnicianId;
    this.reason = reason;
  }

  public UserId technicianId() {
    return technicianId;
  }

  public Optional<UserId> previousTechnicianId() {
    return Optional.ofNullable(previousTechnicianId);
  }

  public Optional<String> reason() {
    return Optional.ofNullable(reason);
  }

  public boolean isReassignment() {
    return previousTechnicianId != null;
  }

  @Override
  public EventType type() {
    return EventType.ASSIGNED;
  }

  @Override
  public String description() {
    String what =
        previousTechnicianId == null
            ? "Assigned to Technician " + technicianId
            : "Reassigned from " + previousTechnicianId + " to " + technicianId;
    return reason == null ? what : what + " (" + reason + ")";
  }
}
