package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.incident.Feedback;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentSnapshot;
import com.servicedesk.domain.incident.IncidentSnapshot.ClosureData;
import com.servicedesk.domain.incident.IncidentSnapshot.NoteData;
import com.servicedesk.domain.incident.IncidentSnapshot.ResolutionData;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.UserId;
import java.util.List;

/**
 * Converts between {@link IncidentEntity} rows and {@link IncidentSnapshot}s (the incident's
 * Memento). The incident itself is never touched here: {@code Incident.snapshot()} and {@code
 * Incident.restore()} keep control of its internals.
 */
final class IncidentMapper {

  private IncidentMapper() {}

  static IncidentSnapshot toSnapshot(IncidentEntity e) {
    List<NoteData> notes =
        e.notes.stream()
            .map(
                n ->
                    new NoteData(
                        new UserId(n.authorId), NoteType.valueOf(n.noteType), n.body, n.createdAt))
            .toList();
    ResolutionData resolution =
        e.resolutionSummary == null
            ? null
            : new ResolutionData(
                e.resolutionSummary, e.resolutionRootCause, userId(e.resolvedBy), e.resolvedAt);
    Feedback feedback =
        e.feedbackRating == null
            ? null
            : new Feedback(e.feedbackRating, e.feedbackComment, e.feedbackAt);
    ClosureData closure =
        e.closedAt == null ? null : new ClosureData(userId(e.closedBy), e.closedAt, e.closureNote);
    return new IncidentSnapshot(
        new IncidentId(e.id),
        new UserId(e.reporterId),
        e.title,
        e.description,
        e.serviceCode,
        Level.valueOf(e.impact),
        Level.valueOf(e.urgency),
        e.affectedUsers,
        e.categoryCode,
        e.priority == null ? null : Priority.valueOf(e.priority),
        IncidentStatus.valueOf(e.status),
        userId(e.assigneeId),
        e.createdAt,
        e.acknowledgedAt,
        e.resolvedAt,
        e.updatedAt,
        e.reopenCount,
        notes,
        resolution,
        feedback,
        closure);
  }

  static void copy(IncidentSnapshot s, IncidentEntity e) {
    e.reporterId = s.reporterId().value();
    e.title = s.title();
    e.description = s.description();
    e.serviceCode = s.serviceCode();
    e.impact = s.impact().name();
    e.urgency = s.urgency().name();
    e.affectedUsers = s.affectedUsers();
    e.categoryCode = s.categoryCode();
    e.priority = s.priority() == null ? null : s.priority().name();
    e.status = s.status().name();
    e.assigneeId = value(s.assigneeId());
    e.createdAt = s.createdAt();
    e.acknowledgedAt = s.acknowledgedAt();
    e.resolvedAt = s.resolvedAt();
    e.updatedAt = s.updatedAt();
    e.reopenCount = s.reopenCount();

    ResolutionData r = s.resolution();
    e.resolutionSummary = r == null ? null : r.summary();
    e.resolutionRootCause = r == null ? null : r.rootCause();
    e.resolvedBy = r == null ? null : value(r.resolvedBy());

    Feedback f = s.feedback();
    e.feedbackRating = f == null ? null : f.rating();
    e.feedbackComment = f == null ? null : f.comment();
    e.feedbackAt = f == null ? null : f.givenAt();

    ClosureData c = s.closure();
    e.closedBy = c == null ? null : value(c.closedBy());
    e.closedAt = c == null ? null : c.closedAt();
    e.closureNote = c == null ? null : c.note();

    copyNotes(s.notes(), e);
  }

  /**
   * Notes are append-only in the domain, so normally only the new ones are added (no rewriting of
   * history rows). If the stored list is somehow longer, it is rebuilt from the snapshot.
   */
  private static void copyNotes(List<NoteData> notes, IncidentEntity e) {
    if (e.notes.size() > notes.size()) {
      e.notes.clear();
    }
    for (int k = e.notes.size(); k < notes.size(); k++) {
      NoteData n = notes.get(k);
      e.notes.add(
          new IncidentEntity.NoteValue(
              n.authorId().value(), n.type().name(), n.text(), n.createdAt()));
    }
  }

  private static UserId userId(String id) {
    return id == null ? null : new UserId(id);
  }

  private static String value(UserId id) {
    return id == null ? null : id.value();
  }
}
