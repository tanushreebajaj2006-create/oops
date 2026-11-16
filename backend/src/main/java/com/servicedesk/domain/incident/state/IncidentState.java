package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.Set;

/**
 * <b>State pattern</b>: one implementation per lifecycle state.
 *
 * <p>Each method represents an action. A state either returns the <i>next</i> state (the action is
 * legal) or throws {@link com.servicedesk.domain.exception.InvalidStateTransitionException}. The
 * {@code Incident} never contains a {@code switch} on its status: it simply asks its current state
 * object. Adding a new state means adding a class, not editing a growing if/else chain.
 *
 * <p>States are stateless singletons; all incident data stays in {@code Incident}.
 */
public interface IncidentState {

  IncidentStatus status();

  /** Actions that are legal from this state (used to show the right buttons in the UI). */
  Set<IncidentAction> allowedActions();

  default boolean allows(IncidentAction action) {
    return allowedActions().contains(action);
  }

  IncidentState classify();

  IncidentState assign();

  IncidentState acknowledge();

  IncidentState startWork();

  IncidentState requestInfo();

  IncidentState provideInfo();

  IncidentState resolve();

  IncidentState verify();

  IncidentState reopen();

  IncidentState close();

  IncidentState cancel();
}
