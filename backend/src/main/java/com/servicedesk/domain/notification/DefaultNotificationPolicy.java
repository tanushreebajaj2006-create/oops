package com.servicedesk.domain.notification;

import static com.servicedesk.domain.notification.Audience.ASSIGNEE;
import static com.servicedesk.domain.notification.Audience.ESCALATION_TARGET;
import static com.servicedesk.domain.notification.Audience.PREVIOUS_ASSIGNEE;
import static com.servicedesk.domain.notification.Audience.REPORTER;
import static com.servicedesk.domain.notification.Audience.SUPERVISORS;
import static com.servicedesk.domain.notification.NotificationRule.to;

import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.incident.event.FeedbackGiven;
import com.servicedesk.domain.incident.event.IncidentAssigned;
import com.servicedesk.domain.incident.event.IncidentClassified;
import com.servicedesk.domain.incident.event.IncidentClosed;
import com.servicedesk.domain.incident.event.IncidentCreated;
import com.servicedesk.domain.incident.event.IncidentEscalated;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.incident.event.IncidentResolved;
import com.servicedesk.domain.incident.event.IncidentStatusChanged;
import com.servicedesk.domain.incident.event.NoteAdded;
import com.servicedesk.domain.incident.event.PriorityChanged;
import com.servicedesk.domain.incident.event.SlaAlertRaised;
import com.servicedesk.domain.priority.Priority;
import java.util.ArrayList;
import java.util.List;

/**
 * The built-in rules. Principles: the reporter hears about progress, the technician hears about
 * work, supervisors only hear about trouble; SMS is reserved for things that cannot wait (P1/P2
 * assignments, SLA violations, escalations).
 *
 * <table>
 *   <caption>Who is notified</caption>
 *   <tr><th>Event</th><th>Reporter</th><th>Technician</th><th>Supervisors</th></tr>
 *   <tr><td>Created</td><td>in-app, e-mail</td><td></td><td></td></tr>
 *   <tr><td>Assigned</td><td>in-app</td><td>in-app, e-mail (+SMS if P1/P2)</td><td></td></tr>
 *   <tr><td>Priority changed</td><td>in-app</td><td>in-app, e-mail</td><td></td></tr>
 *   <tr><td>Info requested</td><td>in-app, e-mail</td><td></td><td></td></tr>
 *   <tr><td>Resolved</td><td>in-app, e-mail</td><td></td><td></td></tr>
 *   <tr><td>SLA warning</td><td></td><td>in-app, e-mail (+SMS if P1)</td><td></td></tr>
 *   <tr><td>SLA violated</td><td></td><td>all channels</td><td>in-app, e-mail</td></tr>
 *   <tr><td>Escalated</td><td></td><td>in-app</td><td>target: all channels</td></tr>
 * </table>
 *
 * The {@code switch} below covers every event class; because {@code IncidentEvent} is sealed the
 * compiler rejects it if a new event type is added and not handled here.
 */
public class DefaultNotificationPolicy implements NotificationPolicy {

  private static final String IN_APP = InAppChannel.NAME;
  private static final String EMAIL = EmailChannel.NAME;
  private static final String SMS = SmsChannel.NAME;

  @Override
  public List<NotificationRule> rulesFor(IncidentEvent event) {
    return switch (event) {
      case IncidentCreated e -> List.of(to(REPORTER, IN_APP, EMAIL).includingActor());
      case IncidentClassified e -> List.of();
      case PriorityChanged e ->
          e.isChange() ? List.of(to(REPORTER, IN_APP), to(ASSIGNEE, IN_APP, EMAIL)) : List.of();
      case IncidentAssigned e -> assigned(e);
      case IncidentStatusChanged e -> statusChanged(e);
      case NoteAdded e ->
          e.noteType() == NoteType.COMMENT
              ? List.of(to(REPORTER, IN_APP), to(ASSIGNEE, IN_APP))
              : List.of(); // investigation notes are internal
      case IncidentResolved e -> List.of(to(REPORTER, IN_APP, EMAIL));
      case IncidentClosed e -> List.of(to(REPORTER, IN_APP), to(ASSIGNEE, IN_APP));
      case FeedbackGiven e -> List.of(to(ASSIGNEE, IN_APP));
      case SlaAlertRaised e -> slaAlert(e);
      case IncidentEscalated e ->
          List.of(to(ESCALATION_TARGET, IN_APP, EMAIL, SMS), to(ASSIGNEE, IN_APP));
    };
  }

  private static List<NotificationRule> assigned(IncidentAssigned e) {
    List<NotificationRule> rules = new ArrayList<>();
    rules.add(urgent(e) ? to(ASSIGNEE, IN_APP, EMAIL, SMS) : to(ASSIGNEE, IN_APP, EMAIL));
    rules.add(to(REPORTER, IN_APP));
    if (e.isReassignment()) {
      rules.add(to(PREVIOUS_ASSIGNEE, IN_APP));
    }
    return rules;
  }

  private static List<NotificationRule> statusChanged(IncidentStatusChanged e) {
    return switch (e.action()) {
      case ACKNOWLEDGE -> List.of(to(REPORTER, IN_APP));
      case REQUEST_INFO -> List.of(to(REPORTER, IN_APP, EMAIL));
      case PROVIDE_INFO, VERIFY -> List.of(to(ASSIGNEE, IN_APP));
      case REOPEN -> List.of(to(ASSIGNEE, IN_APP, EMAIL));
      case CANCEL -> List.of(to(ASSIGNEE, IN_APP), to(REPORTER, IN_APP));
      default -> List.of();
    };
  }

  private static List<NotificationRule> slaAlert(SlaAlertRaised e) {
    if (e.isViolation()) {
      return List.of(to(ASSIGNEE, IN_APP, EMAIL, SMS), to(SUPERVISORS, IN_APP, EMAIL));
    }
    boolean p1 = e.priority().filter(p -> p == Priority.P1).isPresent();
    return List.of(p1 ? to(ASSIGNEE, IN_APP, EMAIL, SMS) : to(ASSIGNEE, IN_APP, EMAIL));
  }

  private static boolean urgent(IncidentEvent e) {
    return e.priority().filter(p -> p == Priority.P1 || p == Priority.P2).isPresent();
  }
}
