package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.User;

/** Sends notifications by e-mail through whatever {@link EmailSender} adapter is plugged in. */
public class EmailChannel implements NotificationChannel {

  public static final String NAME = "EMAIL";

  private final EmailSender sender;

  public EmailChannel(EmailSender sender) {
    this.sender = Require.notNull(sender, "E-mail sender");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public boolean canReach(User recipient) {
    return recipient.email() != null;
  }

  @Override
  public void deliver(User recipient, Notification n) {
    String subject = (n.urgent() ? "[URGENT] " : "") + n.subject();
    sender.send(recipient.email(), subject, n.body());
  }
}
