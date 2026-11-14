package com.servicedesk.domain.exception;

/**
 * Raised by an e-mail / SMS adapter when the external provider cannot be reached or rejects the
 * message (the brief's "network failure" case). The dispatcher catches it per recipient, so one
 * failed delivery never cancels the business action or the other notifications.
 */
public class NotificationDeliveryException extends DomainException {

  public NotificationDeliveryException(String message) {
    super("NOTIFICATION_DELIVERY_FAILED", message);
  }
}
