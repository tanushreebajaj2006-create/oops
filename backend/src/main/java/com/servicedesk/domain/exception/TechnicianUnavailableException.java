package com.servicedesk.domain.exception;

/** Raised when work is given to a technician who is inactive or marked unavailable. */
public class TechnicianUnavailableException extends DomainException {

  public TechnicianUnavailableException(String message) {
    super("TECHNICIAN_UNAVAILABLE", message);
  }
}
