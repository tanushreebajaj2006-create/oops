package com.servicedesk.domain.audit;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * One immutable line of the audit trail, with exactly the fields the brief asks for: timestamp,
 * user, action, incident, previous state, new state and description.
 *
 * <p>Most entries come from incident events ({@link #from(IncidentEvent)}); administrative actions
 * that are not about one incident (changing the assignment strategy, deactivating a user) use
 * {@link #adminAction}, with no incident id.
 *
 * @param sequence position in the log, assigned by the {@link AuditLog} (0 before storing)
 * @param timestamp when it happened
 * @param actorId who did it
 * @param actorRole their role at the time
 * @param action machine-readable action, e.g. {@code ASSIGNED} or {@code STRATEGY_CHANGED}
 * @param incidentId the incident concerned, or {@code null} for administrative actions
 * @param previousStatus incident status before, or {@code null}
 * @param newStatus incident status after, or {@code null}
 * @param description human-readable explanation
 */
public record AuditEntry(
    long sequence,
    Instant timestamp,
    UserId actorId,
    Role actorRole,
    String action,
    IncidentId incidentId,
    IncidentStatus previousStatus,
    IncidentStatus newStatus,
    String description) {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

  public AuditEntry {
    Require.notNull(timestamp, "Timestamp");
    Require.notNull(actorId, "Actor");
    Require.notNull(actorRole, "Actor role");
    action = Require.notBlank(action, "Action").toUpperCase();
    description = Require.notBlank(description, "Description");
  }

  /** Converts an incident event into an audit entry (not yet stored). */
  public static AuditEntry from(IncidentEvent event) {
    Require.notNull(event, "Event");
    return new AuditEntry(
        0,
        event.occurredAt(),
        event.actorId(),
        event.actorRole(),
        event.type().name(),
        event.incidentId(),
        event.previousStatus().orElse(null),
        event.newStatus(),
        event.description());
  }

  /** An administrative action not tied to a single incident. */
  public static AuditEntry adminAction(User actor, String action, String description, Instant at) {
    Require.notNull(actor, "Actor");
    return new AuditEntry(0, at, actor.id(), actor.role(), action, null, null, null, description);
  }

  /** Same entry with the sequence number the log assigned. */
  public AuditEntry withSequence(long seq) {
    return new AuditEntry(
        seq,
        timestamp,
        actorId,
        actorRole,
        action,
        incidentId,
        previousStatus,
        newStatus,
        description);
  }

  public Optional<IncidentId> incident() {
    return Optional.ofNullable(incidentId);
  }

  /** "10:07 - INC1024 - Assigned to Technician T205 [CLASSIFIED -> ASSIGNED]". */
  public String format(ZoneId zone) {
    StringBuilder sb = new StringBuilder(TIME.withZone(zone).format(timestamp)).append(" - ");
    if (incidentId != null && !description.startsWith(incidentId.value())) {
      sb.append(incidentId).append(" - ");
    }
    sb.append(description);
    if (previousStatus != null && newStatus != null && previousStatus != newStatus) {
      sb.append(" [").append(previousStatus).append(" -> ").append(newStatus).append(']');
    }
    return sb.toString();
  }
}
