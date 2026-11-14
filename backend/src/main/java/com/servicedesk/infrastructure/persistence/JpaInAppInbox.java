package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.event.EventType;
import com.servicedesk.domain.notification.InAppInbox;
import com.servicedesk.domain.notification.Notification;
import com.servicedesk.domain.user.UserId;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link InAppInbox} backed by the {@code notifications} table. */
@Repository
@Transactional
public class JpaInAppInbox implements InAppInbox {

  private final SpringDataNotificationRepository jpa;

  public JpaInAppInbox(SpringDataNotificationRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public InboxItem add(Notification n) {
    NotificationEntity saved =
        jpa.save(
            new NotificationEntity(
                n.recipientId().value(),
                n.channel(),
                cut(n.subject(), 300),
                cut(n.body(), 4000),
                n.incidentId().value(),
                n.eventType().name(),
                n.urgent(),
                n.createdAt()));
    return new InboxItem(saved.id, n, false);
  }

  @Override
  @Transactional(readOnly = true)
  public List<InboxItem> itemsFor(UserId recipient) {
    return jpa.findByRecipientIdOrderByIdDesc(recipient.value()).stream()
        .map(JpaInAppInbox::toItem)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long unreadCount(UserId recipient) {
    return jpa.countByRecipientIdAndReadFalse(recipient.value());
  }

  @Override
  public boolean markRead(UserId recipient, long itemId) {
    return jpa.findByIdAndRecipientId(itemId, recipient.value())
        .map(
            e -> {
              e.read = true;
              return true;
            })
        .orElse(false);
  }

  private static InboxItem toItem(NotificationEntity e) {
    Notification n =
        new Notification(
            new UserId(e.recipientId),
            e.channel,
            e.subject,
            e.body,
            new IncidentId(e.incidentId),
            EventType.valueOf(e.eventType),
            e.urgent,
            e.createdAt);
    return new InboxItem(e.id, n, e.read);
  }

  private static String cut(String s, int max) {
    return s.length() <= max ? s : s.substring(0, max - 3) + "...";
  }
}
