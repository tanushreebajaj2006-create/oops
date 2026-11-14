package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;

/**
 * A fix that worked for a similar incident in the past.
 *
 * @param source the past incident
 * @param score 0-1 similarity to the current incident
 * @param summary what was done
 * @param rootCause why it happened (may be null)
 */
public record ResolutionSuggestion(
    Incident source, double score, String summary, String rootCause) {

  public ResolutionSuggestion {
    Require.notNull(source, "Source incident");
    Require.notBlank(summary, "Summary");
  }
}
