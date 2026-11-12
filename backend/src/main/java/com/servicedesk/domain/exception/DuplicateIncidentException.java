package com.servicedesk.domain.exception;

/** Raised when an incident is rejected because it duplicates an existing open incident. */
public class DuplicateIncidentException extends DomainException {

  public DuplicateIncidentException(String message) {
    super("DUPLICATE_INCIDENT", message);
  }
}
