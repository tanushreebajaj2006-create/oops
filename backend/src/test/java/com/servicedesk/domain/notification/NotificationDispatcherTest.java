package com.servicedesk.domain.notification;

import static com.servicedesk.domain.support.TestData.minutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.NotificationDeliveryException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.event.EventType;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.incident.event.SlaAlertRaised;
import com.servicedesk.domain.notification.DeliveryResult.Status;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.InMemorySlaAlertLog;
import com.servicedesk.domain.sla.SlaAlert;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaMonitor;
import com.servicedesk.domain.sla.SlaPolicyCatalog;
import com.servicedesk.domain.support.InMemoryUserDirectory;
import com.servicedesk.domain.support.TestData;
import com.servicedesk.domain.user.Administrator;
import com.servicedesk.domain.user.TeamLead;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notifications")
class NotificationDispatcherTest {

  /** Records e-mails / SMS instead of sending; can be switched to fail. */
  static final class Recorder implements EmailSender, SmsSender {
    final List<String> sent = new ArrayList<>();
    boolean failing;

    @Override
    public void send(String to, String subject, String body) {
      if (failing) {
        throw new NotificationDeliveryException("provider down");
      }
      sent.add(to + " | " + subject);
    }

    @Override
    public void send(String phone, String text) {
      if (failing) {
        throw new NotificationDeliveryException("provider down");
      }
      sent.add(phone + " | " + text);
    }
  }

  private final TestData data = new TestData();
  private final Recorder email = new Recorder();
  private final Recorder sms = new Recorder();
  private final InMemoryInAppInbox inbox = new InMemoryInAppInbox();
  private final Technician tech = TestData.tech("T205", 5, 4, TestData.skill("NETWORK", 4));
  private final TeamLead lead =
      new TeamLead(new UserId("L301"), "Meera L", "meera@corp.com", "h", List.of(), 10, 3);
  private final Administrator admin =
      new Administrator(new UserId("A001"), "Admin", "admin@corp.com", "h");
  private final InMemoryUserDirectory users =
      new InMemoryUserDirectory().add(data.employee, tech, lead, admin);
  private NotificationDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    tech.changePhone("+91 98765-43210");
    lead.changePhone("+919800000001");
    dispatcher =
        new NotificationDispatcher(
            new DefaultNotificationPolicy(),
            new NotificationFactory(),
            users,
            List.of(new InAppChannel(inbox), new EmailChannel(email), new SmsChannel(sms)));
  }

  private List<DeliveryResult> dispatchAll(Incident i) {
    List<DeliveryResult> all = new ArrayList<>();
    for (IncidentEvent e : i.pullEvents()) {
      all.addAll(dispatcher.dispatch(e));
    }
    return all;
  }

  private static List<String> summary(List<DeliveryResult> results) {
    return results.stream()
        .map(r -> r.recipientId() + ":" + r.channel() + ":" + r.status())
        .toList();
  }

  @Test
  @DisplayName("TC-68 reporter gets a creation confirmation by in-app and e-mail")
  void creationConfirmation() {
    Incident i = data.reported(data.payment);
    assertThat(summary(dispatchAll(i))).containsExactly("E102:IN_APP:SENT", "E102:EMAIL:SENT");
    assertThat(email.sent)
        .singleElement()
        .asString()
        .contains("asha@corp.com")
        .contains("received");
    assertThat(inbox.unreadCount(data.employee.id())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "TC-69 P1 assignment: technician by in-app, e-mail and SMS; reporter in-app; the system actor"
          + " is never notified")
  void urgentAssignment() {
    Incident i = data.classified(data.network, Priority.P1);
    i.pullEvents();
    i.assignTo(tech, data.system, minutes(1), "SKILL_BASED: NETWORK skill 4/5");
    List<DeliveryResult> results = dispatchAll(i);
    assertThat(summary(results))
        .containsExactlyInAnyOrder(
            "T205:IN_APP:SENT", "T205:EMAIL:SENT", "T205:SMS:SENT", "E102:IN_APP:SENT");
    assertThat(sms.sent)
        .singleElement()
        .asString()
        .startsWith("+919876543210 | ServiceDesk: INC2000 [P1] assigned to you");
    InAppInbox.InboxItem item = inbox.itemsFor(tech.id()).get(0);
    assertThat(item.notification().urgent()).isTrue();
    assertThat(item.notification().body()).contains("Why you: SKILL_BASED: NETWORK skill 4/5");
    assertThat(email.sent).singleElement().asString().contains("[URGENT]");
  }

  @Test
  @DisplayName("TC-70 a P3 assignment sends no SMS; a user without a phone is skipped, not failed")
  void routineAssignmentAndUnreachable() {
    Incident i = data.classified(data.network, Priority.P3);
    i.pullEvents();
    i.assignTo(tech, data.system, minutes(1));
    assertThat(summary(dispatchAll(i))).doesNotContain("T205:SMS:SENT");

    tech.changePhone(null);
    Incident urgent = data.classified(data.network, Priority.P1);
    urgent.pullEvents();
    urgent.assignTo(tech, data.system, minutes(1));
    List<DeliveryResult> r = dispatchAll(urgent);
    assertThat(r)
        .filteredOn(x -> x.channel().equals("SMS"))
        .singleElement()
        .satisfies(
            x -> {
              assertThat(x.status()).isEqualTo(Status.SKIPPED);
              assertThat(x.detail()).contains("not reachable");
            });
  }

  @Test
  @DisplayName(
      "TC-71 when the e-mail provider fails, that delivery is FAILED and in-app still arrives")
  void providerFailureIsIsolated() {
    email.failing = true;
    Incident i = data.reported(data.payment);
    List<DeliveryResult> r = dispatchAll(i);
    assertThat(summary(r)).containsExactly("E102:IN_APP:SENT", "E102:EMAIL:FAILED");
    assertThat(r.get(1).detail()).isEqualTo("provider down");
    assertThat(inbox.unreadCount(data.employee.id())).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "TC-72 SLA violation alerts the technician on every channel and supervisors once each;"
          + " deactivated users are skipped")
  void slaViolationEscalatesToSupervisors() {
    Incident i = data.classified(data.network, Priority.P1);
    i.assignTo(lead, data.system, minutes(1)); // the team lead is also the assignee
    i.pullEvents();
    admin.deactivate();
    data.clock.set(minutes(10));
    SlaMonitor monitor =
        new SlaMonitor(
            new SlaCalculator(SlaPolicyCatalog.standard(), data.clock), new InMemorySlaAlertLog());
    SlaAlert violation = monitor.scan(List.of(i)).violations().get(0);
    SlaAlertRaised event = SlaAlertRaised.of(violation, i);
    assertThat(event.type()).isEqualTo(EventType.SLA_VIOLATION);

    List<DeliveryResult> r = dispatcher.dispatch(event);
    // lead is both ASSIGNEE and SUPERVISOR: one delivery per channel, not two
    assertThat(summary(r)).containsExactly("L301:IN_APP:SENT", "L301:EMAIL:SENT", "L301:SMS:SENT");
    Notification n = inbox.itemsFor(lead.id()).get(0).notification();
    assertThat(n.subject()).isEqualTo("INC2000 [P1] SLA VIOLATED");
    assertThat(n.urgent()).isTrue();
  }

  @Test
  @DisplayName(
      "TC-73 in-app inbox: newest first, unread count, only the owner can mark an item read")
  void inbox() {
    Incident i = data.classified(data.network, Priority.P2);
    i.assignTo(tech, data.system, minutes(1));
    i.acknowledge(tech, minutes(2));
    dispatchAll(i);
    List<InAppInbox.InboxItem> items = inbox.itemsFor(data.employee.id());
    assertThat(items)
        .extracting(x -> x.notification().eventType())
        .containsExactly(EventType.STATUS_CHANGED, EventType.ASSIGNED, EventType.CREATED);
    assertThat(inbox.unreadCount(data.employee.id())).isEqualTo(3);
    long id = items.get(0).id();
    assertThat(inbox.markRead(tech.id(), id)).isFalse();
    assertThat(inbox.markRead(data.employee.id(), id)).isTrue();
    assertThat(inbox.unreadCount(data.employee.id())).isEqualTo(2);
  }

  @Test
  @DisplayName("TC-74 a new channel and a custom policy plug in without changing the dispatcher")
  void extensibility() {
    List<String> chat = new ArrayList<>();
    NotificationChannel teams =
        new NotificationChannel() {
          @Override
          public String name() {
            return "TEAMS";
          }

          @Override
          public boolean canReach(User recipient) {
            return true;
          }

          @Override
          public void deliver(User recipient, Notification n) {
            chat.add(recipient.id() + ": " + n.subject());
          }
        };
    NotificationPolicy everyoneOnTeams =
        e -> List.of(new NotificationRule(Audience.REPORTER, Set.of("TEAMS", "FAX"), true));
    NotificationDispatcher custom =
        new NotificationDispatcher(everyoneOnTeams, new NotificationFactory(), users, List.of())
            .register(teams);

    Incident i = data.reported(data.payment);
    List<DeliveryResult> r = custom.dispatch(i.pullEvents().get(0));
    assertThat(chat).containsExactly("E102: INC2000 received: Payment app unavailable");
    assertThat(r)
        .filteredOn(x -> x.channel().equals("FAX"))
        .singleElement()
        .extracting(DeliveryResult::detail)
        .isEqualTo("channel not configured");
  }

  @Test
  @DisplayName("TC-75 phone numbers are normalised and validated")
  void phoneValidation() {
    tech.changePhone("+44 7700-900123");
    assertThat(tech.phone()).contains("+447700900123");
    assertThatThrownBy(() -> tech.changePhone("call me")).isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> tech.changePhone("12345")).isInstanceOf(InvalidInputException.class);
    tech.changePhone("  ");
    assertThat(tech.phone()).isEmpty();
  }
}
