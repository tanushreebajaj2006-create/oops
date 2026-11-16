package com.servicedesk.domain.priority;

import com.servicedesk.domain.common.Require;

/**
 * Everything a {@link PriorityStrategy} is allowed to look at. Bundling the inputs in one immutable
 * value keeps strategies independent of the {@code Incident} class: they can be unit-tested with
 * plain values and reused for "what-if" previews in the UI before an incident is even saved.
 *
 * @param impact how much of the business is affected
 * @param urgency how quickly the business needs it fixed
 * @param businessCriticality importance of the affected IT service
 * @param affectedUsers number of people reported as affected (at least 1)
 */
public record PriorityFactors(
    Level impact, Level urgency, Level businessCriticality, int affectedUsers) {

  public PriorityFactors {
    Require.notNull(impact, "Impact");
    Require.notNull(urgency, "Urgency");
    Require.notNull(businessCriticality, "Business criticality");
    Require.inRange(affectedUsers, 1, Integer.MAX_VALUE, "Affected users");
  }

  /**
   * Maps the head-count onto the same 4-step scale as the other factors, using orders of magnitude
   * so that 5 vs 8 users makes no difference but 5 vs 500 does.
   *
   * <pre>
   *   1-9  -> LOW      10-99  -> MEDIUM
   *   100-999 -> HIGH  1000+  -> CRITICAL
   * </pre>
   */
  public Level affectedUsersLevel() {
    if (affectedUsers >= 1000) {
      return Level.CRITICAL;
    }
    if (affectedUsers >= 100) {
      return Level.HIGH;
    }
    if (affectedUsers >= 10) {
      return Level.MEDIUM;
    }
    return Level.LOW;
  }
}
