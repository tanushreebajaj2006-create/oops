package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import java.util.List;

/**
 * An incident that may describe the same problem as the one being examined.
 *
 * @param incident the other incident
 * @param score 0-1, higher = more likely the same underlying problem
 * @param verdict how strong the match is
 * @param reasons why, in plain words ("same service PAYMENT", "shared terms: fail, payment")
 */
public record RelatedIncident(
    Incident incident, double score, Verdict verdict, List<String> reasons) {

  /** Strength of a match. */
  public enum Verdict {
    /** Very probably the same problem - consider linking or cancelling one. */
    LIKELY_DUPLICATE,
    /** Possibly connected - worth a look. */
    RELATED
  }

  public RelatedIncident {
    Require.notNull(incident, "Incident");
    Require.notNull(verdict, "Verdict");
    reasons = List.copyOf(reasons);
  }
}
