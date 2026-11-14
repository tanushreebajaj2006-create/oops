package com.servicedesk.domain.notification;

/** Who, relative to the incident, a notification rule is aimed at. */
public enum Audience {
  /** The employee who reported the incident. */
  REPORTER,
  /** The technician currently holding the incident. */
  ASSIGNEE,
  /** The technician who held it before a re-assignment. */
  PREVIOUS_ASSIGNEE,
  /** Team leads and administrators (everyone with the RECEIVE_ESCALATIONS permission). */
  SUPERVISORS,
  /** The person an incident was escalated to. */
  ESCALATION_TARGET
}
