package com.servicedesk.domain.exception;

/** Raised when a user tries an action their role or relationship to the incident does not allow. */
public class UnauthorizedOperationException extends DomainException {

  public UnauthorizedOperationException(String message) {
    super("UNAUTHORIZED_OPERATION", message);
  }
}
