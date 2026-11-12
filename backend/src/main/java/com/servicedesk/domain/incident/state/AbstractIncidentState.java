package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.Set;

/**
 * Default behaviour: every action is illegal. Concrete states override only the actions they
 * permit, so "what is forbidden" is the safe default and each state class stays tiny.
 */
abstract class AbstractIncidentState implements IncidentState {

  private final IncidentStatus status;
  private final Set<IncidentAction> allowed;

  protected AbstractIncidentState(IncidentStatus status, Set<IncidentAction> allowed) {
    this.status = status;
    this.allowed = Set.copyOf(allowed);
  }

  @Override
  public final IncidentStatus status() {
    return status;
  }

  @Override
  public final Set<IncidentAction> allowedActions() {
    return allowed;
  }

  private IncidentState reject(IncidentAction action) {
    throw new InvalidStateTransitionException(status.name(), action.name());
  }

  @Override
  public IncidentState classify() {
    return reject(IncidentAction.CLASSIFY);
  }

  @Override
  public IncidentState assign() {
    return reject(IncidentAction.ASSIGN);
  }

  @Override
  public IncidentState acknowledge() {
    return reject(IncidentAction.ACKNOWLEDGE);
  }

  @Override
  public IncidentState startWork() {
    return reject(IncidentAction.START_WORK);
  }

  @Override
  public IncidentState requestInfo() {
    return reject(IncidentAction.REQUEST_INFO);
  }

  @Override
  public IncidentState provideInfo() {
    return reject(IncidentAction.PROVIDE_INFO);
  }

  @Override
  public IncidentState resolve() {
    return reject(IncidentAction.RESOLVE);
  }

  @Override
  public IncidentState verify() {
    return reject(IncidentAction.VERIFY);
  }

  @Override
  public IncidentState reopen() {
    return reject(IncidentAction.REOPEN);
  }

  @Override
  public IncidentState close() {
    return reject(IncidentAction.CLOSE);
  }

  @Override
  public IncidentState cancel() {
    return reject(IncidentAction.CANCEL);
  }

  @Override
  public String toString() {
    return status.name();
  }
}
