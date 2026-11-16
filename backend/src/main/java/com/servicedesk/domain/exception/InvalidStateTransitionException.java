package com.servicedesk.domain.exception;

/**
 * Raised when an incident is asked to perform an action its current lifecycle state forbids, e.g.
 * closing an incident that is still {@code NEW}.
 */
public class InvalidStateTransitionException extends DomainException {

  private final String fromState;
  private final String action;

  public InvalidStateTransitionException(String fromState, String action) {
    super(
        "INVALID_STATE_TRANSITION",
        "Cannot perform '" + action + "' while incident is in state " + fromState);
    this.fromState = fromState;
    this.action = action;
  }

  public String fromState() {
    return fromState;
  }

  public String action() {
    return action;
  }
}
