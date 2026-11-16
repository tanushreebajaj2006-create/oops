package com.servicedesk.domain.notification;

import com.servicedesk.domain.exception.NotificationDeliveryException;
import com.servicedesk.domain.user.User;

/**
 * A way of reaching a person: in-app, e-mail, SMS... (<b>interface</b> + <b>polymorphism</b>).
 *
 * <p>The {@link NotificationDispatcher} talks only to this interface. Adding a new mechanism
 * (Slack, push notifications, WhatsApp) means writing one new implementation and registering it -
 * the dispatcher, the policy and the incident code stay untouched.
 */
public interface NotificationChannel {

  /** Stable upper-case name used in the {@link NotificationPolicy}, e.g. {@code SMS}. */
  String name();

  /** Whether this user can be reached on this channel at all (e.g. SMS needs a phone number). */
  boolean canReach(User recipient);

  /**
   * Delivers the notification.
   *
   * @throws NotificationDeliveryException if the underlying provider fails
   */
  void deliver(User recipient, Notification notification);
}
