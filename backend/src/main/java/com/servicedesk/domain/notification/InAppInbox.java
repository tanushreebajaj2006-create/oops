package com.servicedesk.domain.notification;

import com.servicedesk.domain.user.UserId;
import java.util.List;

/**
 * Storage behind the in-app notification bell (<b>Repository</b> contract). Phase 4 stores it in
 * the database; {@link InMemoryInAppInbox} serves tests.
 */
public interface InAppInbox {

  /** Stores a notification and returns the item with its id. */
  InboxItem add(Notification notification);

  /** Newest first. */
  List<InboxItem> itemsFor(UserId recipient);

  long unreadCount(UserId recipient);

  /**
   * Marks one item read. Only the owner may do so.
   *
   * @return false if the item does not exist or belongs to someone else
   */
  boolean markRead(UserId recipient, long itemId);

  /** One stored notification and whether it has been read. */
  record InboxItem(long id, Notification notification, boolean read) {}
}
