package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Something important that happened to an incident. Events are the <b>subject</b> side of the
 * <b>Observer pattern</b>: the incident records them, an {@link IncidentEventPublisher} hands them
 * to every {@link IncidentEventListener} (audit trail, notifications, later analytics), and the
 * incident never needs to know who is listening.
 *
 * <p>The class is {@code sealed}: the compiler knows the complete list of event kinds, so a {@code
 * switch} over events (e.g. in the notification policy) is checked for completeness - forgetting a
 * new event type becomes a compile error instead of a silent gap. Each subclass is a genuine
 * <i>is-a</i> specialisation that adds its own data and its own {@link #description()}
 * (<b>inheritance</b> + <b>polymorphism</b>).
 *
 * <p>Events are immutable.
 */
public abstract sealed class IncidentEvent
    permits IncidentCreated,
        IncidentClassified,
        PriorityChanged,
        IncidentAssigned,
        IncidentStatusChanged,
        NoteAdded,
        IncidentResolved,
        IncidentClosed,
        FeedbackGiven,
        SlaAlertRaised,
        IncidentEscalated {

  private final EventHeader header;

  protected IncidentEvent(EventHeader header) {
    this.header = Require.notNull(header, "Event header");
  }

  /** The kind of event, for storage and filtering. */
  public abstract EventType type();

  /** Human-readable one-liner for the audit trail, e.g. "Assigned to T205 (SKILL_BASED ...)". */
  public abstract String description();

  public EventHeader header() {
    return header;
  }

  public IncidentId incidentId() {
    return header.incidentId();
  }

  public Instant occurredAt() {
    return header.occurredAt();
  }

  public UserId actorId() {
    return header.actorId();
  }

  public Role actorRole() {
    return header.actorRole();
  }

  public UserId reporterId() {
    return header.reporterId();
  }

  public Optional<UserId> assigneeId() {
    return Optional.ofNullable(header.assigneeId());
  }

  public Optional<Priority> priority() {
    return Optional.ofNullable(header.priority());
  }

  public Optional<IncidentStatus> previousStatus() {
    return Optional.ofNullable(header.previousStatus());
  }

  public IncidentStatus newStatus() {
    return header.newStatus();
  }

  public String title() {
    return header.title();
  }

  @Override
  public String toString() {
    return occurredAt() + " " + incidentId() + " " + type() + ": " + description();
  }
}
