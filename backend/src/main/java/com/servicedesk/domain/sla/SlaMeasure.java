package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * State of one SLA clock (response or resolution) at a moment in time.
 *
 * @param kind which clock
 * @param startedAt when the clock started (incident creation)
 * @param dueAt the deadline
 * @param completedAt when the work was done, or {@code null} if the clock is still running
 * @param status health of the clock
 * @param elapsedFraction share of the allowed time used so far (0.0 = just started, 1.0 = at the
 *     deadline, above 1.0 = overdue)
 */
public record SlaMeasure(
    SlaKind kind,
    Instant startedAt,
    Instant dueAt,
    Instant completedAt,
    SlaStatus status,
    double elapsedFraction) {

  public SlaMeasure {
    Require.notNull(kind, "SLA kind");
    Require.notNull(startedAt, "Start time");
    Require.notNull(dueAt, "Due time");
    Require.notNull(status, "SLA status");
  }

  public Optional<Instant> completed() {
    return Optional.ofNullable(completedAt);
  }

  public boolean isRunning() {
    return completedAt == null && status != SlaStatus.NOT_APPLICABLE;
  }

  /**
   * Time left before the deadline at {@code now} (negative once overdue). For a finished clock, the
   * margin by which it was met (positive) or missed (negative).
   */
  public Duration remaining(Instant now) {
    Instant reference = completedAt != null ? completedAt : now;
    return Duration.between(reference, dueAt);
  }

  /** How long the deadline was (or is being) missed by; zero if not breached. */
  public Duration overdue(Instant now) {
    Duration left = remaining(now);
    return left.isNegative() ? left.negated() : Duration.ZERO;
  }
}
