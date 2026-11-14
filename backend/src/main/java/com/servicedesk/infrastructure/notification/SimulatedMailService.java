package com.servicedesk.infrastructure.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-in for a third-party e-mail provider SDK (think SendGrid or Amazon SES). It deliberately
 * has its <i>own</i> API - request/response objects, HTTP-style status codes, a list of recipients,
 * HTML content - that does not match the domain's {@code EmailSender} port. {@link
 * MailServiceEmailAdapter} bridges the two, which is exactly the situation the <b>Adapter
 * pattern</b> exists for.
 *
 * <p>Nothing is really sent: accepted mail is logged and kept in an outbox the demo UI and tests
 * can inspect. {@link #setOnline(boolean)} simulates a provider outage.
 */
public class SimulatedMailService {

  private static final Logger LOG = LoggerFactory.getLogger(SimulatedMailService.class);

  /** The provider's request format. */
  public record MailRequest(String from, List<String> to, String subject, String htmlContent) {}

  /** The provider's response format: 202 = accepted, 4xx/5xx = rejected. */
  public record MailResponse(int statusCode, String messageId, String error) {

    public boolean accepted() {
      return statusCode >= 200 && statusCode < 300;
    }
  }

  private final List<MailRequest> outbox = new ArrayList<>();
  private volatile boolean online = true;

  public synchronized MailResponse submit(MailRequest request) {
    if (!online) {
      return new MailResponse(503, null, "Service unavailable");
    }
    if (request.to() == null || request.to().isEmpty()) {
      return new MailResponse(400, null, "No recipients");
    }
    outbox.add(request);
    String id = UUID.randomUUID().toString();
    LOG.info("[SIMULATED EMAIL] to={} subject=\"{}\" id={}", request.to(), request.subject(), id);
    return new MailResponse(202, id, null);
  }

  public void setOnline(boolean online) {
    this.online = online;
  }

  public synchronized List<MailRequest> outbox() {
    return List.copyOf(outbox);
  }
}
