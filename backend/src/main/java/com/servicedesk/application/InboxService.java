package com.servicedesk.application;

import com.servicedesk.domain.notification.InAppInbox;
import com.servicedesk.domain.notification.InAppInbox.InboxItem;
import com.servicedesk.domain.user.UserId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The signed-in user's in-app notifications (the bell). Users only ever see their own. */
@Service
@Transactional
public class InboxService {

  private final InAppInbox inbox;

  public InboxService(InAppInbox inbox) {
    this.inbox = inbox;
  }

  @Transactional(readOnly = true)
  public List<InboxItem> items(UserId me) {
    return inbox.itemsFor(me);
  }

  @Transactional(readOnly = true)
  public long unread(UserId me) {
    return inbox.unreadCount(me);
  }

  public boolean markRead(UserId me, long itemId) {
    return inbox.markRead(me, itemId);
  }
}
