package com.servicedesk.domain.sla;

/** The two clocks every incident runs against. */
public enum SlaKind {
  /** Created -> first acknowledged by the assigned technician. */
  RESPONSE,
  /** Created -> resolved. */
  RESOLUTION
}
