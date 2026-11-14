package com.servicedesk.domain.priority;

import com.servicedesk.domain.common.Require;

/**
 * The formula suggested in the brief: {@code score = Impact x Urgency x Business Criticality}.
 *
 * <p>Each factor is 1-4 (see {@link Level#weight()}), so the score ranges from 1 to 64. Default
 * thresholds:
 *
 * <table>
 *   <caption>Score bands</caption>
 *   <tr><th>Score</th><th>Priority</th><th>Typical example</th></tr>
 *   <tr><td>36-64</td><td>P1</td><td>HIGH x HIGH x CRITICAL (36): main payment system down</td></tr>
 *   <tr><td>18-35</td><td>P2</td><td>HIGH x HIGH x MEDIUM (18): one department cannot work</td></tr>
 *   <tr><td>8-17</td><td>P3</td><td>MEDIUM x MEDIUM x MEDIUM (8): slow but usable</td></tr>
 *   <tr><td>1-7</td><td>P4</td><td>LOW x MEDIUM x MEDIUM (4): cosmetic issue</td></tr>
 * </table>
 *
 * <b>Rationale</b>: multiplying (rather than adding) means a single LOW factor pulls the score down
 * sharply - a total outage of an unimportant test service should not page anyone at night. The
 * thresholds can be overridden by the administrator through the second constructor.
 */
public final class MatrixPriorityStrategy implements PriorityStrategy {

  public static final String NAME = "MATRIX";
  public static final int MAX_SCORE = 64;

  private final int p1Min;
  private final int p2Min;
  private final int p3Min;

  public MatrixPriorityStrategy() {
    this(36, 18, 8);
  }

  /**
   * @param p1Min lowest score that is P1
   * @param p2Min lowest score that is P2
   * @param p3Min lowest score that is P3 (anything below is P4)
   */
  public MatrixPriorityStrategy(int p1Min, int p2Min, int p3Min) {
    Require.inRange(p3Min, 2, MAX_SCORE - 2, "P3 threshold");
    Require.inRange(p2Min, p3Min + 1, MAX_SCORE - 1, "P2 threshold");
    Require.inRange(p1Min, p2Min + 1, MAX_SCORE, "P1 threshold");
    this.p1Min = p1Min;
    this.p2Min = p2Min;
    this.p3Min = p3Min;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Impact x Urgency x Business criticality (1-64); P1 >= "
        + p1Min
        + ", P2 >= "
        + p2Min
        + ", P3 >= "
        + p3Min;
  }

  @Override
  public PriorityAssessment assess(PriorityFactors f) {
    int i = f.impact().weight();
    int u = f.urgency().weight();
    int c = f.businessCriticality().weight();
    int score = i * u * c;
    Priority priority = toPriority(score);
    String why =
        "Impact %d x Urgency %d x Criticality %d = %d -> %s"
            .formatted(i, u, c, score, priority.name());
    return new PriorityAssessment(priority, score, NAME, why);
  }

  private Priority toPriority(int score) {
    if (score >= p1Min) {
      return Priority.P1;
    }
    if (score >= p2Min) {
      return Priority.P2;
    }
    if (score >= p3Min) {
      return Priority.P3;
    }
    return Priority.P4;
  }
}
