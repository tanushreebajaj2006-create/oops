package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.User;
import java.time.Instant;

/**
 * A timestamped note on an incident. Immutable: history is never edited, only appended to.
 * Incidents own their notes (<b>composition</b>): a note never exists without its incident.
 */
public record Note(User author, NoteType type, String text, Instant createdAt) {

  public static final int MAX_LENGTH = 4000;

  public Note {
    Require.notNull(author, "Note author");
    Require.notNull(type, "Note type");
    text = Require.maxLength(Require.notBlank(text, "Note text"), MAX_LENGTH, "Note text");
    Require.notNull(createdAt, "Note time");
  }
}
