package com.servicedesk.domain.incident;

/**
 * Supplies the next incident id. The domain only knows this contract; the persistence layer
 * implements it with a database sequence, and tests use a simple counter.
 */
@FunctionalInterface
public interface IncidentIdGenerator {
  IncidentId nextId();
}
