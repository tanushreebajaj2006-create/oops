package com.servicedesk.domain.audit;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;

/**
 * Filter for searching the audit log. Every field is optional ({@code null} = do not filter); the
 * conditions that are set must all match.
 *
 * @param incidentId only entries about this incident
 * @param actorId only entries performed by this user
 * @param action only this action, e.g. {@code SLA_VIOLATION}
 * @param from only entries at or after this time
 * @param to only entries before this time
 */
public record AuditQuery(
    IncidentId incidentId, UserId actorId, String action, Instant from, Instant to) {

  public static AuditQuery all() {
    return new AuditQuery(null, null, null, null, null);
  }

  public static AuditQuery forIncident(IncidentId id) {
    return new AuditQuery(id, null, null, null, null);
  }

  public AuditQuery byActor(UserId actor) {
    return new AuditQuery(incidentId, actor, action, from, to);
  }

  public AuditQuery withAction(String newAction) {
    return new AuditQuery(incidentId, actorId, newAction, from, to);
  }

  public AuditQuery between(Instant start, Instant end) {
    return new AuditQuery(incidentId, actorId, action, start, end);
  }

  public boolean matches(AuditEntry e) {
    return (incidentId == null || incidentId.equals(e.incidentId()))
        && (actorId == null || actorId.equals(e.actorId()))
        && (action == null || action.equalsIgnoreCase(e.action()))
        && (from == null || !e.timestamp().isBefore(from))
        && (to == null || e.timestamp().isBefore(to));
  }
}
