package com.servicedesk.infrastructure.notification;

import com.servicedesk.domain.exception.NotificationDeliveryException;
import com.servicedesk.domain.notification.EmailSender;
import com.servicedesk.infrastructure.notification.SimulatedMailService.MailRequest;
import com.servicedesk.infrastructure.notification.SimulatedMailService.MailResponse;
import java.util.List;

/**
 * <b>Adapter pattern</b>: makes the provider's {@link SimulatedMailService} look like the domain's
 * {@link EmailSender}.
 *
 * <p>It translates in both directions: plain-text {@code (to, subject, body)} becomes the
 * provider's {@link MailRequest} with escaped HTML content and a sender address; the provider's
 * status code becomes either a normal return or a domain {@link NotificationDeliveryException}.
 * Switching to a real provider means writing another adapter - the domain is untouched.
 */
public class MailServiceEmailAdapter implements EmailSender {

  private final SimulatedMailService provider;
  private final String fromAddress;

  public MailServiceEmailAdapter(SimulatedMailService provider, String fromAddress) {
    this.provider = provider;
    this.fromAddress = fromAddress;
  }

  @Override
  public void send(String toAddress, String subject, String body) {
    MailRequest request =
        new MailRequest(fromAddress, List.of(toAddress), subject, "<p>" + escape(body) + "</p>");
    MailResponse response;
    try {
      response = provider.submit(request);
    } catch (RuntimeException e) {
      throw new NotificationDeliveryException("E-mail provider unreachable: " + e.getMessage());
    }
    if (!response.accepted()) {
      throw new NotificationDeliveryException(
          "E-mail provider rejected message (" + response.statusCode() + "): " + response.error());
    }
  }

  static String escape(String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
