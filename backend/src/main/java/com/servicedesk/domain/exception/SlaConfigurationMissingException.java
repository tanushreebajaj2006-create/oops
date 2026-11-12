package com.servicedesk.domain.exception;

/** Raised when no SLA policy matches an incident's service and priority. */
public class SlaConfigurationMissingException extends DomainException {

  public SlaConfigurationMissingException(String message) {
    super("SLA_CONFIGURATION_MISSING", message);
  }
}
