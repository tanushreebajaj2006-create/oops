package com.servicedesk.domain.notification;

import com.servicedesk.domain.exception.NotificationDeliveryException;

/**
 * <b>Port</b>: what the domain needs from an e-mail provider, expressed in the domain's own terms.
 * Infrastructure supplies an <b>Adapter</b> that translates this call into whatever API the real
 * (or simulated) provider has, so the domain never depends on a vendor SDK.
 */
@FunctionalInterface
public interface EmailSender {

  /**
   * @throws NotificationDeliveryException if the provider is unreachable or rejects the message
   */
  void send(String toAddress, String subject, String body);
}
