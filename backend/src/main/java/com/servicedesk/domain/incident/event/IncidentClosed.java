package com.servicedesk.domain.incident.event;

import java.util.Optional;

/** The incident reached its final CLOSED state. */
public final class IncidentClosed extends IncidentEvent {

  private final String closingNote;

  public IncidentClosed(EventHeader header, String closingNote) {
    super(header);
    this.closingNote = closingNote == null || closingNote.isBlank() ? null : closingNote.strip();
  }

  public Optional<String> closingNote() {
    return Optional.ofNullable(closingNote);
  }

  @Override
  public EventType type() {
    return EventType.CLOSED;
  }

  @Override
  public String description() {
    return closingNote == null ? "Incident CLOSED" : "Incident CLOSED: " + closingNote;
  }
}
