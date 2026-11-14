package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.event.EventType;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;

/**
 * One message for one recipient on one channel. Built by {@link NotificationFactory}; immutable.
 *
 * @param recipientId who receives it
 * @param channel channel name, e.g. {@code EMAIL}
 * @param subject short title (e-mail subject, in-app headline)
 * @param body full text
 * @param incidentId the incident it is about
 * @param eventType what triggered it
 * @param urgent true for SLA violations, escalations and P1 assignments
 * @param createdAt when it was produced
 */
public record Notification(
    UserId recipientId,
    String channel,
    String subject,
    String body,
    IncidentId incidentId,
    EventType eventType,
    boolean urgent,
    Instant createdAt) {

  public Notification {
    Require.notNull(recipientId, "Recipient");
    channel = Require.notBlank(channel, "Channel").toUpperCase();
    subject = Require.notBlank(subject, "Subject");
    body = Require.notBlank(body, "Body");
    Require.notNull(incidentId, "Incident id");
    Require.notNull(eventType, "Event type");
    Require.notNull(createdAt, "Creation time");
  }
}
