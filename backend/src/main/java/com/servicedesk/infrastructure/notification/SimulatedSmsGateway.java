package com.servicedesk.infrastructure.notification;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-in for a third-party SMS gateway (think Twilio). Its API uses MSISDN numbers (digits only,
 * no {@code +}), a sender id and a result object - again different from the domain's {@code
 * SmsSender} port, so {@link SmsGatewayAdapter} adapts it.
 */
public class SimulatedSmsGateway {

  private static final Logger LOG = LoggerFactory.getLogger(SimulatedSmsGateway.class);

  /** A message as the gateway stores it. */
  public record SmsMessage(String senderId, String msisdn, String text) {}

  /** The gateway's answer. */
  public record TransmitResult(boolean accepted, String reason) {}

  private final List<SmsMessage> sent = new ArrayList<>();
  private volatile boolean online = true;

  public synchronized TransmitResult transmit(String senderId, String msisdn, String text) {
    if (!online) {
      return new TransmitResult(false, "Gateway offline");
    }
    if (msisdn == null || !msisdn.matches("\\d{8,15}")) {
      return new TransmitResult(false, "Invalid MSISDN");
    }
    sent.add(new SmsMessage(senderId, msisdn, text));
    LOG.info("[SIMULATED SMS] to={} text=\"{}\"", msisdn, text);
    return new TransmitResult(true, "queued");
  }

  public void setOnline(boolean online) {
    this.online = online;
  }

  public synchronized List<SmsMessage> sent() {
    return List.copyOf(sent);
  }
}
