package com.servicedesk.domain.notification;

import com.servicedesk.domain.incident.event.IncidentEvent;
import java.util.List;

/**
 * Decides who is told about an event and on which channels (<b>Strategy</b>: "notification
 * selection"). An organisation with different rules - say, SMS for every P2 - provides another
 * implementation; nothing else changes.
 */
@FunctionalInterface
public interface NotificationPolicy {

  /** Rules for this event; an empty list means nobody is notified (it is still audited). */
  List<NotificationRule> rulesFor(IncidentEvent event);
}
