package com.servicedesk.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Row of {@code incidents} plus its owned notes. References to users, the service and the category
 * are plain key columns (with database foreign keys); the domain {@code Incident} is rebuilt from
 * this via {@code IncidentSnapshot}, never exposed directly.
 */
@Entity
@Table(name = "incidents")
public class IncidentEntity {

  @Id String id;

  @Column(name = "reporter_id", nullable = false)
  String reporterId;

  @Column(nullable = false)
  String title;

  @Column(nullable = false)
  String description;

  @Column(name = "service_code", nullable = false)
  String serviceCode;

  @Column(nullable = false)
  String impact;

  @Column(nullable = false)
  String urgency;

  @Column(name = "affected_users", nullable = false)
  int affectedUsers;

  @Column(name = "category_code")
  String categoryCode;

  String priority;

  @Column(nullable = false)
  String status;

  @Column(name = "assignee_id")
  String assigneeId;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "acknowledged_at")
  Instant acknowledgedAt;

  @Column(name = "resolved_at")
  Instant resolvedAt;

  @Column(name = "updated_at", nullable = false)
  Instant updatedAt;

  @Column(name = "reopen_count", nullable = false)
  int reopenCount;

  @Column(name = "resolution_summary")
  String resolutionSummary;

  @Column(name = "resolution_root_cause")
  String resolutionRootCause;

  @Column(name = "resolved_by")
  String resolvedBy;

  @Column(name = "feedback_rating")
  Integer feedbackRating;

  @Column(name = "feedback_comment")
  String feedbackComment;

  @Column(name = "feedback_at")
  Instant feedbackAt;

  @Column(name = "closed_by")
  String closedBy;

  @Column(name = "closed_at")
  Instant closedAt;

  @Column(name = "closure_note")
  String closureNote;

  /** Optimistic lock: a stale update fails instead of silently overwriting someone's change. */
  @Version Long version;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "incident_notes", joinColumns = @JoinColumn(name = "incident_id"))
  @OrderColumn(name = "note_order")
  List<NoteValue> notes = new ArrayList<>();

  protected IncidentEntity() {}

  IncidentEntity(String id) {
    this.id = id;
  }

  /** One row of {@code incident_notes}. */
  @Embeddable
  public static class NoteValue {

    @Column(name = "author_id", nullable = false)
    String authorId;

    @Column(name = "note_type", nullable = false)
    String noteType;

    @Column(nullable = false)
    String body;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected NoteValue() {}

    NoteValue(String authorId, String noteType, String body, Instant createdAt) {
      this.authorId = authorId;
      this.noteType = noteType;
      this.body = body;
      this.createdAt = createdAt;
    }
  }
}
