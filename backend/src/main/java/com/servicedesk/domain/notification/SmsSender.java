package com.servicedesk.domain.notification;

import com.servicedesk.domain.exception.NotificationDeliveryException;

/** <b>Port</b> for an SMS provider; implemented by an <b>Adapter</b> in infrastructure. */
@FunctionalInterface
public interface SmsSender {

  /** Maximum length of one SMS segment; longer texts are shortened by {@link SmsChannel}. */
  int MAX_LENGTH = 160;

  /**
   * @throws NotificationDeliveryException if the provider is unreachable or rejects the message
   */
  void send(String phoneNumber, String text);
}
