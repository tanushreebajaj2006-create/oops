package com.servicedesk.domain.exception;

/** Raised when an incident is structurally invalid or an operation does not make sense for it. */
public class InvalidIncidentException extends DomainException {

  public InvalidIncidentException(String message) {
    super("INVALID_INCIDENT", message);
  }
}
