package com.servicedesk.domain.user;

/** Fine-grained capabilities. Each {@link User} subclass decides which ones it has. */
public enum Permission {
  REPORT_INCIDENT,
  WORK_ON_INCIDENTS,
  CLASSIFY_INCIDENTS,
  ASSIGN_INCIDENTS,
  VIEW_ALL_INCIDENTS,
  MANAGE_USERS,
  CONFIGURE_SYSTEM,
  VIEW_REPORTS,
  VIEW_AUDIT_LOG,
  RECEIVE_ESCALATIONS
}
