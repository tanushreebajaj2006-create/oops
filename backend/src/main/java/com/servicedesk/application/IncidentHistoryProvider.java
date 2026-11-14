package com.servicedesk.application;

import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.incident.event.IncidentEventListener;
import com.servicedesk.domain.intelligence.IncidentHistory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Supplies the {@link IncidentHistory} the intelligent features learn from: the most recent
 * resolved incidents (up to {@value IncidentQuery#MAX_LIMIT}).
 *
 * <p>Building it means loading hundreds of incidents, so the result is cached for {@link #TTL}.
 * History changes slowly (one more resolved ticket barely moves a median), so a one-minute-old view
 * is perfectly good, and assignment stays fast.
 */
@Component
public class IncidentHistoryProvider implements Supplier<IncidentHistory>, IncidentEventListener {

  static final Duration TTL = Duration.ofSeconds(60);

  private final IncidentRepository incidents;
  private final Clock clock;
  private volatile IncidentHistory cached;
  private volatile Instant loadedAt = Instant.MIN;

  public IncidentHistoryProvider(IncidentRepository incidents, Clock clock) {
    this.incidents = incidents;
    this.clock = clock;
  }

  @Override
  public IncidentHistory get() {
    Instant now = clock.instant();
    IncidentHistory h = cached;
    if (h == null || loadedAt.plus(TTL).isBefore(now) || now.isBefore(loadedAt)) {
      h =
          IncidentHistory.of(
              incidents.search(
                  IncidentQuery.all()
                      .withStatuses(ReportService.RESOLVED)
                      .page(IncidentQuery.MAX_LIMIT, 0)));
      cached = h;
      loadedAt = now;
    }
    return h;
  }

  /** Forget the cached history (e.g. after a bulk import, or in tests). */
  public void invalidate() {
    cached = null;
  }

  /**
   * Observer: an incident was resolved, reopened, rated or closed, so what history teaches has
   * changed - drop the cache so the next request sees it.
   */
  @Override
  public void onEvent(IncidentEvent event) {
    switch (event.type()) {
      case RESOLVED, CLOSED, FEEDBACK_GIVEN -> invalidate();
      case STATUS_CHANGED -> {
        if (event.previousStatus().map(s -> !s.isOpen()).orElse(false)) {
          invalidate(); // reopened
        }
      }
      default -> {
        // other events do not change resolved history
      }
    }
  }
}
