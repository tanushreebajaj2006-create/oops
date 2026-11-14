package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import java.time.Instant;
import java.util.List;

/**
 * Predicted chance that an open incident will miss its resolution deadline.
 *
 * @param incident the incident
 * @param level LOW, MEDIUM or HIGH
 * @param projectedRatio projected total time / allowed time (above 1.0 = expected to breach)
 * @param projectedResolution when the incident is expected to be resolved
 * @param dueAt the resolution deadline
 * @param reasons what drives the prediction, in plain words
 */
public record SlaRisk(
    Incident incident,
    Level level,
    double projectedRatio,
    Instant projectedResolution,
    Instant dueAt,
    List<String> reasons) {

  /** Risk band. */
  public enum Level {
    LOW,
    MEDIUM,
    HIGH
  }

  public SlaRisk {
    Require.notNull(incident, "Incident");
    Require.notNull(level, "Level");
    reasons = List.copyOf(reasons);
  }
}
