package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.sla.SlaAlert;
import com.servicedesk.domain.user.SystemActor;
import java.util.Optional;

/**
 * The SLA monitor raised a warning (deadline approaching) or a violation (deadline missed). Unlike
 * the other events it is produced by the system, not by an action on the incident.
 */
public final class SlaAlertRaised extends IncidentEvent {

  private final SlaAlert alert;

  public SlaAlertRaised(EventHeader header, SlaAlert alert) {
    super(header);
    this.alert = Require.notNull(alert, "SLA alert");
    Require.that(
        alert.incidentId().equals(header.incidentId()), "SLA alert belongs to another incident");
  }

  /** Builds the event for an alert, taking the snapshot from the incident's current state. */
  public static SlaAlertRaised of(SlaAlert alert, Incident incident) {
    Require.notNull(incident, "Incident");
    EventHeader header =
        new EventHeader(
            incident.id(),
            alert.detectedAt(),
            SystemActor.INSTANCE.id(),
            SystemActor.INSTANCE.role(),
            incident.reporter().id(),
            incident.assignee().map(t -> t.id()).orElse(null),
            incident.title(),
            incident.priority().orElse(null),
            incident.status(),
            incident.status());
    return new SlaAlertRaised(header, alert);
  }

  public SlaAlert alert() {
    return alert;
  }

  public boolean isViolation() {
    return alert.type() == SlaAlert.Type.VIOLATION;
  }

  public Optional<String> responsibleTechnician() {
    return alert.assigneeId().map(Object::toString);
  }

  @Override
  public EventType type() {
    return isViolation() ? EventType.SLA_VIOLATION : EventType.SLA_WARNING;
  }

  @Override
  public String description() {
    return alert.describe();
  }
}
