package com.servicedesk.infrastructure.notification;

import com.servicedesk.domain.exception.NotificationDeliveryException;
import com.servicedesk.domain.notification.SmsSender;
import com.servicedesk.infrastructure.notification.SimulatedSmsGateway.TransmitResult;

/**
 * <b>Adapter pattern</b>: presents the {@link SimulatedSmsGateway} as the domain's {@link
 * SmsSender}, converting {@code +919876543210} into the gateway's MSISDN format and gateway
 * rejections into {@link NotificationDeliveryException}.
 */
public class SmsGatewayAdapter implements SmsSender {

  private final SimulatedSmsGateway gateway;
  private final String senderId;

  public SmsGatewayAdapter(SimulatedSmsGateway gateway, String senderId) {
    this.gateway = gateway;
    this.senderId = senderId;
  }

  @Override
  public void send(String phoneNumber, String text) {
    String msisdn = phoneNumber == null ? null : phoneNumber.replace("+", "");
    TransmitResult result;
    try {
      result = gateway.transmit(senderId, msisdn, text);
    } catch (RuntimeException e) {
      throw new NotificationDeliveryException("SMS gateway unreachable: " + e.getMessage());
    }
    if (!result.accepted()) {
      throw new NotificationDeliveryException("SMS gateway rejected message: " + result.reason());
    }
  }
}
