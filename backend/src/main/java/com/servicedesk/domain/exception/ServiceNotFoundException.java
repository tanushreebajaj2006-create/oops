package com.servicedesk.domain.exception;

/** Raised when an incident references an IT service that does not exist or is retired. */
public class ServiceNotFoundException extends DomainException {

  public ServiceNotFoundException(String message) {
    super("SERVICE_NOT_FOUND", message);
  }
}
