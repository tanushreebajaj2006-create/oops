package com.servicedesk.domain.incident;

/**
 * The lifecycle states, used for storage, filtering and display. The <i>behaviour</i> of each state
 * lives in the matching {@code IncidentState} class.
 */
public enum IncidentStatus {
  NEW,
  CLASSIFIED,
  ASSIGNED,
  ACKNOWLEDGED,
  IN_PROGRESS,
  AWAITING_INFO,
  RESOLVED,
  VERIFIED,
  CLOSED,
  CANCELLED;

  /** Open means someone still has work to do on the incident. */
  public boolean isOpen() {
    return this != RESOLVED && this != VERIFIED && this != CLOSED && this != CANCELLED;
  }
}
