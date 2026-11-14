package com.servicedesk.domain.notification;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.UserId;

/**
 * Outcome of trying to deliver one notification.
 *
 * @param recipientId who it was for
 * @param channel which channel was used
 * @param status sent, skipped (recipient not reachable on this channel) or failed (provider error)
 * @param detail reason for a skip or failure; {@code "ok"} when sent
 */
public record DeliveryResult(UserId recipientId, String channel, Status status, String detail) {

  /** What happened to the notification. */
  public enum Status {
    SENT,
    SKIPPED,
    FAILED
  }

  public DeliveryResult {
    Require.notNull(recipientId, "Recipient");
    Require.notBlank(channel, "Channel");
    Require.notNull(status, "Status");
    Require.notBlank(detail, "Detail");
  }

  public static DeliveryResult sent(UserId to, String channel) {
    return new DeliveryResult(to, channel, Status.SENT, "ok");
  }

  public static DeliveryResult skipped(UserId to, String channel, String why) {
    return new DeliveryResult(to, channel, Status.SKIPPED, why);
  }

  public static DeliveryResult failed(UserId to, String channel, String why) {
    return new DeliveryResult(to, channel, Status.FAILED, why);
  }
}
