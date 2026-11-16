package com.servicedesk.domain.user;

/** The kinds of user the system knows about. {@code SYSTEM} is used for automated actions. */
public enum Role {
  EMPLOYEE,
  TECHNICIAN,
  TEAM_LEAD,
  ADMINISTRATOR,
  SYSTEM
}
