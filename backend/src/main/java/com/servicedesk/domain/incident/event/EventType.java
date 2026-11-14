package com.servicedesk.domain.incident.event;

/** Stable names for every kind of {@link IncidentEvent}; used for audit filtering and storage. */
public enum EventType {
  CREATED,
  CLASSIFIED,
  DETAILS_UPDATED,
  PRIORITY_CHANGED,
  ASSIGNED,
  STATUS_CHANGED,
  NOTE_ADDED,
  RESOLVED,
  CLOSED,
  FEEDBACK_GIVEN,
  SLA_WARNING,
  SLA_VIOLATION,
  ESCALATED
}
