package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.User;

/**
 * Sends short text alerts. Only users with a phone number can be reached; the text is the subject
 * (SMS is for "look at this now", the details are in the app), cut to one 160-character segment.
 */
public class SmsChannel implements NotificationChannel {

  public static final String NAME = "SMS";

  private final SmsSender sender;

  public SmsChannel(SmsSender sender) {
    this.sender = Require.notNull(sender, "SMS sender");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public boolean canReach(User recipient) {
    return recipient.phone().isPresent();
  }

  @Override
  public void deliver(User recipient, Notification n) {
    String text = "ServiceDesk: " + n.subject();
    if (text.length() > SmsSender.MAX_LENGTH) {
      text = text.substring(0, SmsSender.MAX_LENGTH - 3) + "...";
    }
    sender.send(recipient.phone().orElseThrow(), text);
  }
}
