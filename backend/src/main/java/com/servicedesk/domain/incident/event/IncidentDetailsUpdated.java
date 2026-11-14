package com.servicedesk.domain.incident.event;

import java.util.List;

/** The reporter or a supervisor corrected the incident's details (title, impact, ...). */
public final class IncidentDetailsUpdated extends IncidentEvent {

  private final List<String> changes;

  public IncidentDetailsUpdated(EventHeader header, List<String> changes) {
    super(header);
    this.changes = List.copyOf(changes);
  }

  /** Human-readable list such as {@code "impact HIGH -> CRITICAL"}. */
  public List<String> changes() {
    return changes;
  }

  @Override
  public EventType type() {
    return EventType.DETAILS_UPDATED;
  }

  @Override
  public String description() {
    return "Details updated: " + String.join(", ", changes);
  }
}
