package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.User;

/** Puts the notification in the user's in-app inbox (the bell icon in the web UI). */
public class InAppChannel implements NotificationChannel {

  public static final String NAME = "IN_APP";

  private final InAppInbox inbox;

  public InAppChannel(InAppInbox inbox) {
    this.inbox = Require.notNull(inbox, "Inbox");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public boolean canReach(User recipient) {
    return true;
  }

  @Override
  public void deliver(User recipient, Notification notification) {
    inbox.add(notification);
  }
}
