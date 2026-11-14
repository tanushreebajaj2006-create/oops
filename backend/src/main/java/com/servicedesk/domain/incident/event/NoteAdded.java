package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.NoteType;

/** An investigation note or comment was added (the text itself stays on the incident). */
public final class NoteAdded extends IncidentEvent {

  private final NoteType noteType;

  public NoteAdded(EventHeader header, NoteType noteType) {
    super(header);
    this.noteType = Require.notNull(noteType, "Note type");
  }

  public NoteType noteType() {
    return noteType;
  }

  @Override
  public EventType type() {
    return EventType.NOTE_ADDED;
  }

  @Override
  public String description() {
    return noteType == NoteType.INVESTIGATION ? "Investigation note added" : "Comment added";
  }
}
