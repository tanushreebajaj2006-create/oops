package com.servicedesk.domain.incident;

/** Why a note was added to an incident. */
public enum NoteType {
  /** Technician's investigation notes; internal, hidden from the reporter. */
  INVESTIGATION(false),
  /** Technician asks the reporter for more information. */
  INFO_REQUEST(true),
  /** Reporter answers an information request. */
  INFO_RESPONSE(true),
  /** General comment visible to everyone involved. */
  COMMENT(true);

  private final boolean visibleToReporter;

  NoteType(boolean visibleToReporter) {
    this.visibleToReporter = visibleToReporter;
  }

  public boolean visibleToReporter() {
    return visibleToReporter;
  }
}
