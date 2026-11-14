package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;

/**
 * Facts shared by every incident event: what happened to which incident, when, by whom, and a
 * snapshot of who was involved at that moment.
 *
 * <p>The snapshot (reporter, assignee, priority, title) makes each event self-contained: an audit
 * entry or a notification can be produced without loading the incident again, and it reflects the
 * situation <i>at the time</i>, not as it is today.
 *
 * @param incidentId the incident
 * @param occurredAt when it happened
 * @param actorId who did it ({@code SYSTEM} for automatic actions)
 * @param actorRole the actor's role at the time
 * @param reporterId who reported the incident
 * @param assigneeId technician holding the incident after the event, or {@code null}
 * @param title incident title, for messages
 * @param priority priority after the event, or {@code null} if not yet prioritised
 * @param previousStatus status before the event, or {@code null} for creation
 * @param newStatus status after the event
 */
public record EventHeader(
    IncidentId incidentId,
    Instant occurredAt,
    UserId actorId,
    Role actorRole,
    UserId reporterId,
    UserId assigneeId,
    String title,
    Priority priority,
    IncidentStatus previousStatus,
    IncidentStatus newStatus) {

  public EventHeader {
    Require.notNull(incidentId, "Incident id");
    Require.notNull(occurredAt, "Event time");
    Require.notNull(actorId, "Actor");
    Require.notNull(actorRole, "Actor role");
    Require.notNull(reporterId, "Reporter");
    Require.notBlank(title, "Title");
    Require.notNull(newStatus, "New status");
  }

  /** True if the event moved the incident to a different lifecycle state. */
  public boolean changedStatus() {
    return previousStatus != newStatus;
  }
}
