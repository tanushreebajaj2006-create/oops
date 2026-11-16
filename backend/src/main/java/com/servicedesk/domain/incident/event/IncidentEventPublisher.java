package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * <b>Subject</b> in the Observer pattern: delivers each event to every subscribed {@link
 * IncidentEventListener}, in subscription order.
 *
 * <p>Design choices:
 *
 * <ul>
 *   <li><b>Fault isolation</b> - if one listener throws (say the e-mail gateway is down), the
 *       others still receive the event; the failure is passed to an error handler instead of
 *       breaking the business operation that caused the event.
 *   <li>Listeners are held in a {@link CopyOnWriteArrayList}: events are published far more often
 *       than listeners change, and iteration is safe even if someone subscribes concurrently (e.g.
 *       the SLA job publishing while a request is running).
 * </ul>
 */
public class IncidentEventPublisher {

  private final List<IncidentEventListener> listeners = new CopyOnWriteArrayList<>();
  private final BiConsumer<IncidentEventListener, RuntimeException> errorHandler;

  /** Publisher that reports listener failures through {@link System.Logger}. */
  public IncidentEventPublisher() {
    this(IncidentEventPublisher::logFailure);
  }

  public IncidentEventPublisher(BiConsumer<IncidentEventListener, RuntimeException> errorHandler) {
    this.errorHandler = Require.notNull(errorHandler, "Error handler");
  }

  public IncidentEventPublisher subscribe(IncidentEventListener listener) {
    Require.notNull(listener, "Listener");
    if (!listeners.contains(listener)) {
      listeners.add(listener);
    }
    return this;
  }

  public void unsubscribe(IncidentEventListener listener) {
    listeners.remove(listener);
  }

  /**
   * Delivers the event to all listeners.
   *
   * @return number of listeners that failed (0 when everything went well)
   */
  public int publish(IncidentEvent event) {
    Require.notNull(event, "Event");
    int failures = 0;
    for (IncidentEventListener listener : listeners) {
      try {
        listener.onEvent(event);
      } catch (RuntimeException e) {
        failures++;
        errorHandler.accept(listener, e);
      }
    }
    return failures;
  }

  /**
   * Publishes several events in order (typically everything {@code Incident.pullEvents()} gave).
   */
  public int publishAll(Collection<? extends IncidentEvent> events) {
    Require.notNull(events, "Events");
    int failures = 0;
    for (IncidentEvent e : events) {
      failures += publish(e);
    }
    return failures;
  }

  public int listenerCount() {
    return listeners.size();
  }

  private static void logFailure(IncidentEventListener listener, RuntimeException e) {
    System.getLogger(IncidentEventPublisher.class.getName())
        .log(
            System.Logger.Level.WARNING,
            "Listener " + listener.listenerName() + " failed: " + e.getMessage(),
            e);
  }
}
