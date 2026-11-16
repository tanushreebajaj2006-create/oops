package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.event.IncidentAssigned;
import com.servicedesk.domain.incident.event.IncidentCreated;
import com.servicedesk.domain.incident.event.IncidentEscalated;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.incident.event.IncidentResolved;
import com.servicedesk.domain.incident.event.IncidentStatusChanged;
import com.servicedesk.domain.incident.event.SlaAlertRaised;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.User;

/**
 * <b>Factory pattern</b>: turns an event into the wording a particular person should read.
 *
 * <p>The same event reads differently to different people - "your incident INC1024 is being handled
 * by T205" for the reporter, "INC1024 [P1] has been assigned to you" for the technician - so the
 * factory takes the {@link Audience} into account. All wording lives here; channels only decide
 * <i>how</i> to deliver, never <i>what</i> to say.
 */
public class NotificationFactory {

  public Notification create(
      IncidentEvent event, User recipient, Audience audience, String channel) {
    Require.notNull(event, "Event");
    Require.notNull(recipient, "Recipient");
    Require.notNull(audience, "Audience");
    String ref = event.incidentId() + priorityTag(event);
    String subject;
    String body;
    switch (event) {
      case IncidentCreated e -> {
        subject = ref + " received: " + e.title();
        body =
            "Hello %s, we have logged your incident '%s' (%s). You will be notified as it progresses."
                .formatted(recipient.fullName(), e.title(), e.incidentId());
      }
      case IncidentAssigned e when audience == Audience.ASSIGNEE -> {
        subject = ref + " assigned to you: " + e.title();
        body =
            "%s has been assigned to you.%s Please acknowledge it."
                .formatted(e.incidentId(), e.reason().map(r -> " Why you: " + r + ".").orElse(""));
      }
      case IncidentAssigned e when audience == Audience.PREVIOUS_ASSIGNEE -> {
        subject = ref + " reassigned to " + e.technicianId();
        body = "%s is no longer in your queue.".formatted(e.incidentId());
      }
      case IncidentAssigned e -> {
        subject = ref + " is being handled by " + e.technicianId();
        body = "A technician has been assigned to your incident '%s'.".formatted(e.title());
      }
      case IncidentStatusChanged e when e.action() == IncidentAction.REQUEST_INFO -> {
        subject = ref + " needs more information from you";
        body =
            "The technician working on '%s' asked: %s"
                .formatted(e.title(), e.detail().orElse("(see the incident)"));
      }
      case IncidentResolved e -> {
        subject = ref + " has been resolved";
        body =
            "Resolution: %s. Please verify the fix, or reopen the incident if the problem persists."
                .formatted(e.summary());
      }
      case SlaAlertRaised e -> {
        subject = ref + (e.isViolation() ? " SLA VIOLATED" : " SLA deadline approaching");
        body = e.description() + ". Incident: " + e.title();
      }
      case IncidentEscalated e -> {
        subject = ref + " escalated to you (level " + e.level() + ")";
        body = "%s: %s. Please take ownership.".formatted(e.title(), e.reason());
      }
      default -> {
        subject = ref + " update: " + event.description();
        body =
            "%s ('%s') is now %s.".formatted(event.incidentId(), event.title(), event.newStatus());
      }
    }
    return new Notification(
        recipient.id(),
        channel,
        subject,
        body,
        event.incidentId(),
        event.type(),
        isUrgent(event),
        event.occurredAt());
  }

  /** Urgent: SLA violations, escalations and anything about a P1 incident. */
  static boolean isUrgent(IncidentEvent event) {
    return switch (event) {
      case SlaAlertRaised e when e.isViolation() -> true;
      case IncidentEscalated e -> true;
      default -> event.priority().filter(p -> p == Priority.P1).isPresent();
    };
  }

  private static String priorityTag(IncidentEvent event) {
    return event.priority().map(p -> " [" + p + "]").orElse("");
  }
}
