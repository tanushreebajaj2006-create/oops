package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import java.time.Duration;

/**
 * The two promises an SLA makes for one priority level.
 *
 * @param response time allowed until a technician acknowledges the incident
 * @param resolution time allowed until the incident is resolved
 */
public record SlaTarget(Duration response, Duration resolution) {

  public SlaTarget {
    Require.notNull(response, "Response target");
    Require.notNull(resolution, "Resolution target");
    Require.that(!response.isNegative() && !response.isZero(), "Response target must be positive");
    Require.that(
        resolution.compareTo(response) >= 0,
        "Resolution target must not be shorter than the response target");
  }

  public static SlaTarget of(Duration response, Duration resolution) {
    return new SlaTarget(response, resolution);
  }

  /** The target that applies to the given kind of SLA clock. */
  public Duration forKind(SlaKind kind) {
    return kind == SlaKind.RESPONSE ? response : resolution;
  }
}
