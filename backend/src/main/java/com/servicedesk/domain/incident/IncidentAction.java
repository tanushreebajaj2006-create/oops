package com.servicedesk.domain.incident;

/** Every lifecycle action. Used to tell the UI which buttons to show for the current state. */
public enum IncidentAction {
  CLASSIFY,
  ASSIGN,
  ACKNOWLEDGE,
  START_WORK,
  REQUEST_INFO,
  PROVIDE_INFO,
  RESOLVE,
  VERIFY,
  REOPEN,
  CLOSE,
  CANCEL
}
