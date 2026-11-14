package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.UserId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link InAppInbox} kept in memory: a {@link HashMap} from recipient to that person's list, so
 * opening one user's bell never scans everyone else's notifications.
 */
public class InMemoryInAppInbox implements InAppInbox {

  private final Map<UserId, List<InboxItem>> byRecipient = new HashMap<>();
  private final AtomicLong ids = new AtomicLong();

  @Override
  public synchronized InboxItem add(Notification notification) {
    Require.notNull(notification, "Notification");
    InboxItem item = new InboxItem(ids.incrementAndGet(), notification, false);
    byRecipient.computeIfAbsent(notification.recipientId(), k -> new ArrayList<>()).add(item);
    return item;
  }

  @Override
  public synchronized List<InboxItem> itemsFor(UserId recipient) {
    List<InboxItem> items = new ArrayList<>(byRecipient.getOrDefault(recipient, List.of()));
    Collections.reverse(items);
    return List.copyOf(items);
  }

  @Override
  public synchronized long unreadCount(UserId recipient) {
    return byRecipient.getOrDefault(recipient, List.of()).stream().filter(i -> !i.read()).count();
  }

  @Override
  public synchronized boolean markRead(UserId recipient, long itemId) {
    List<InboxItem> items = byRecipient.get(recipient);
    if (items == null) {
      return false;
    }
    for (int k = 0; k < items.size(); k++) {
      InboxItem item = items.get(k);
      if (item.id() == itemId) {
        items.set(k, new InboxItem(item.id(), item.notification(), true));
        return true;
      }
    }
    return false;
  }
}
