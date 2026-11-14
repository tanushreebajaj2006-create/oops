package com.servicedesk.domain.incident.event;

/**
 * <b>Observer</b> in the Observer pattern: anything that wants to react to incident events (audit
 * trail, notifications, analytics, a future webhook...) implements this one method and subscribes
 * to the {@link IncidentEventPublisher}. The incident and the publisher never change when a new
 * observer is added.
 */
@FunctionalInterface
public interface IncidentEventListener {

  void onEvent(IncidentEvent event);

  /** Name used in error reports when this listener fails. */
  default String listenerName() {
    return getClass().getSimpleName();
  }
}
