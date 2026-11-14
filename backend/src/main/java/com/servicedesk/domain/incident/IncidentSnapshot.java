package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.List;

/**
 * A plain-data copy of an {@link Incident}'s complete state (<b>Memento pattern</b>).
 *
 * <p>The incident keeps all its fields private and has no setters, yet the persistence layer must
 * be able to store and reload it. Instead of breaking encapsulation, the incident exports a
 * snapshot ({@link Incident#snapshot()}) and can be rebuilt from one ({@link Incident#restore}).
 * References to other aggregates (users, service, category) are stored as ids/codes, exactly as a
 * database row would hold them.
 */
public record IncidentSnapshot(
    IncidentId id,
    UserId reporterId,
    String title,
    String description,
    String serviceCode,
    Level impact,
    Level urgency,
    int affectedUsers,
    String categoryCode,
    Priority priority,
    IncidentStatus status,
    UserId assigneeId,
    Instant createdAt,
    Instant acknowledgedAt,
    Instant resolvedAt,
    Instant updatedAt,
    int reopenCount,
    List<NoteData> notes,
    ResolutionData resolution,
    Feedback feedback,
    ClosureData closure) {

  public IncidentSnapshot {
    Require.notNull(id, "Incident id");
    Require.notNull(reporterId, "Reporter");
    Require.notNull(status, "Status");
    notes = notes == null ? List.of() : List.copyOf(notes);
  }

  /** A note with its author as an id. */
  public record NoteData(UserId authorId, NoteType type, String text, Instant createdAt) {}

  /** The resolution with the resolver as an id. */
  public record ResolutionData(
      String summary, String rootCause, UserId resolvedBy, Instant resolvedAt) {}

  /** The closure with the closer as an id. */
  public record ClosureData(UserId closedBy, Instant closedAt, String note) {}
}
