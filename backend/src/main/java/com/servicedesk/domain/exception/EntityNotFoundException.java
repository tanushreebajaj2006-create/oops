package com.servicedesk.domain.exception;

/** Raised when a requested domain object (incident, user, category...) does not exist. */
public class EntityNotFoundException extends DomainException {

  public EntityNotFoundException(String message) {
    super("NOT_FOUND", message);
  }
}
