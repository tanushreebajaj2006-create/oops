package com.servicedesk.domain.exception;

/**
 * Raised when user-supplied data breaks a validation rule (blank title, rating out of range...).
 */
public class InvalidInputException extends DomainException {

  public InvalidInputException(String message) {
    super("INVALID_INPUT", message);
  }
}
