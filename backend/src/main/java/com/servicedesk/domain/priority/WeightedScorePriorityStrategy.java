package com.servicedesk.domain.priority;

import com.servicedesk.domain.exception.InvalidInputException;
import java.util.Locale;

/**
 * Weighted-average scoring that adds the <b>number of affected users</b> as a fourth factor.
 *
 * <pre>
 *   score = 0.35 x Impact + 0.30 x Urgency + 0.20 x Criticality + 0.15 x AffectedUsersLevel
 * </pre>
 *
 * Every factor is 1-4, so the score is also 1.0-4.0 and reads like "average severity". Bands:
 * {@code >= 3.25 -> P1}, {@code >= 2.50 -> P2}, {@code >= 1.75 -> P3}, otherwise P4.
 *
 * <p><b>Rationale</b>: unlike {@link MatrixPriorityStrategy}, one LOW factor does not dominate, and
 * a problem hitting thousands of users is pushed up even if each user reports it as medium. Impact
 * and urgency carry the most weight because they come from the reporter's description of the real
 * situation; criticality is a static property of the service. Weights can be tuned by the
 * administrator but must add up to 1.
 */
public final class WeightedScorePriorityStrategy implements PriorityStrategy {

  public static final String NAME = "WEIGHTED";

  private static final double EPSILON = 1e-9;

  private final double impactWeight;
  private final double urgencyWeight;
  private final double criticalityWeight;
  private final double usersWeight;

  public WeightedScorePriorityStrategy() {
    this(0.35, 0.30, 0.20, 0.15);
  }

  /**
   * @throws InvalidInputException if a weight is negative or the weights do not sum to 1
   */
  public WeightedScorePriorityStrategy(
      double impactWeight, double urgencyWeight, double criticalityWeight, double usersWeight) {
    if (impactWeight < 0 || urgencyWeight < 0 || criticalityWeight < 0 || usersWeight < 0) {
      throw new InvalidInputException("Priority weights must not be negative");
    }
    double sum = impactWeight + urgencyWeight + criticalityWeight + usersWeight;
    if (Math.abs(sum - 1.0) > 0.001) {
      throw new InvalidInputException("Priority weights must add up to 1.0 but were " + sum);
    }
    this.impactWeight = impactWeight;
    this.urgencyWeight = urgencyWeight;
    this.criticalityWeight = criticalityWeight;
    this.usersWeight = usersWeight;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Weighted average of impact, urgency, criticality and affected users (1.0-4.0)";
  }

  @Override
  public PriorityAssessment assess(PriorityFactors f) {
    Level users = f.affectedUsersLevel();
    double score =
        impactWeight * f.impact().weight()
            + urgencyWeight * f.urgency().weight()
            + criticalityWeight * f.businessCriticality().weight()
            + usersWeight * users.weight();
    // round to 2 decimals so the audit text and the band decision agree exactly
    score = Math.round(score * 100.0) / 100.0;
    Priority priority = toPriority(score);
    String why =
        String.format(
            Locale.ROOT,
            "%.2f*I%d + %.2f*U%d + %.2f*C%d + %.2f*Users%d(%d) = %.2f -> %s",
            impactWeight,
            f.impact().weight(),
            urgencyWeight,
            f.urgency().weight(),
            criticalityWeight,
            f.businessCriticality().weight(),
            usersWeight,
            users.weight(),
            f.affectedUsers(),
            score,
            priority.name());
    return new PriorityAssessment(priority, score, NAME, why);
  }

  private static Priority toPriority(double score) {
    if (score >= 3.25 - EPSILON) {
      return Priority.P1;
    }
    if (score >= 2.50 - EPSILON) {
      return Priority.P2;
    }
    if (score >= 1.75 - EPSILON) {
      return Priority.P3;
    }
    return Priority.P4;
  }
}
