package com.servicedesk.domain.exception;

/** Raised when assigning would push a technician above their maximum number of open incidents. */
public class WorkloadExceededException extends DomainException {

  public WorkloadExceededException(String message) {
    super("WORKLOAD_EXCEEDED", message);
  }
}
