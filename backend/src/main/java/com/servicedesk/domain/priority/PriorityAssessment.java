package com.servicedesk.domain.priority;

import com.servicedesk.domain.common.Require;

/**
 * The result of running a {@link PriorityStrategy}: not just the priority but <i>why</i>. The
 * rationale is written to the audit trail ("Priority calculated as P1: 4 x 4 x 3 = 48") so that
 * managers can trust and challenge automatic decisions.
 *
 * @param priority the computed priority
 * @param score the raw score the strategy produced (scale depends on the strategy)
 * @param strategy name of the strategy that produced it
 * @param rationale human-readable explanation of the calculation
 */
public record PriorityAssessment(
    Priority priority, double score, String strategy, String rationale) {

  public PriorityAssessment {
    Require.notNull(priority, "Priority");
    Require.notBlank(strategy, "Strategy name");
    Require.notBlank(rationale, "Rationale");
  }
}
