package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;

/** A user reported a new incident. */
public final class IncidentCreated extends IncidentEvent {

  private final String serviceCode;

  public IncidentCreated(EventHeader header, String serviceCode) {
    super(header);
    this.serviceCode = Require.notBlank(serviceCode, "Service code");
  }

  public String serviceCode() {
    return serviceCode;
  }

  @Override
  public EventType type() {
    return EventType.CREATED;
  }

  @Override
  public String description() {
    return "%s created by %s %s on service %s"
        .formatted(incidentId(), actorRole(), actorId(), serviceCode);
  }
}
