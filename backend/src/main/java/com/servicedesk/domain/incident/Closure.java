package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.User;
import java.time.Instant;

/** Who closed (or cancelled) an incident, when, and why. */
public record Closure(User closedBy, Instant closedAt, String note) {

  public static final int NOTE_MAX = 2000;

  public Closure {
    Require.notNull(closedBy, "Closed by");
    Require.notNull(closedAt, "Closed at");
    note = Require.maxLength(note, NOTE_MAX, "Closing note");
  }
}
