package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;

/** The technician recorded a fix. */
public final class IncidentResolved extends IncidentEvent {

  private final String summary;

  public IncidentResolved(EventHeader header, String summary) {
    super(header);
    this.summary = Require.notBlank(summary, "Resolution summary");
  }

  public String summary() {
    return summary;
  }

  @Override
  public EventType type() {
    return EventType.RESOLVED;
  }

  @Override
  public String description() {
    return "Incident marked RESOLVED: " + summary;
  }
}
