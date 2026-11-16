package com.servicedesk.domain.priority;

import static com.servicedesk.domain.priority.Level.CRITICAL;
import static com.servicedesk.domain.priority.Level.HIGH;
import static com.servicedesk.domain.priority.Level.LOW;
import static com.servicedesk.domain.priority.Level.MEDIUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.servicedesk.domain.common.StrategyRegistry;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Priority strategies")
class PriorityStrategyTest {

  private final MatrixPriorityStrategy matrix = new MatrixPriorityStrategy();
  private final WeightedScorePriorityStrategy weighted = new WeightedScorePriorityStrategy();

  private static PriorityFactors f(Level i, Level u, Level c, int users) {
    return new PriorityFactors(i, u, c, users);
  }

  @ParameterizedTest(name = "{0} x {1} x {2} = {3} -> {4}")
  @CsvSource({
    "CRITICAL, CRITICAL, CRITICAL, 64, P1",
    "HIGH,     HIGH,     CRITICAL, 36, P1", // lowest P1
    "MEDIUM,   CRITICAL, CRITICAL, 32, P2",
    "HIGH,     HIGH,     MEDIUM,   18, P2", // lowest P2
    "MEDIUM,   MEDIUM,   HIGH,     12, P3",
    "MEDIUM,   MEDIUM,   MEDIUM,    8, P3", // lowest P3
    "LOW,      HIGH,     MEDIUM,    6, P4",
    "LOW,      LOW,      LOW,       1, P4"
  })
  @DisplayName("TC-36 matrix score = I x U x C with P1/P2/P3 boundaries at 36/18/8")
  void matrixBands(Level i, Level u, Level c, int score, Priority expected) {
    PriorityAssessment a = matrix.assess(f(i, u, c, 1));
    assertThat(a.score()).isEqualTo(score);
    assertThat(a.priority()).isEqualTo(expected);
    assertThat(a.strategy()).isEqualTo("MATRIX");
    assertThat(a.rationale()).contains("= " + score).contains(expected.name());
  }

  @Test
  @DisplayName("TC-37 matrix thresholds are configurable but must be strictly descending")
  void matrixThresholdValidation() {
    MatrixPriorityStrategy strict = new MatrixPriorityStrategy(48, 24, 12);
    assertThat(strict.assess(f(HIGH, HIGH, CRITICAL, 1)).priority()).isEqualTo(Priority.P2);
    assertThatThrownBy(() -> new MatrixPriorityStrategy(10, 20, 5))
        .isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> new MatrixPriorityStrategy(70, 20, 5))
        .isInstanceOf(InvalidInputException.class);
  }

  @ParameterizedTest(name = "{0} users -> {1}")
  @CsvSource({
    "1, LOW",
    "9, LOW",
    "10, MEDIUM",
    "99, MEDIUM",
    "100, HIGH",
    "999, HIGH",
    "1000, CRITICAL"
  })
  @DisplayName("TC-38 affected-user count maps to a level by order of magnitude")
  void affectedUsersLevel(int users, Level expected) {
    assertThat(f(LOW, LOW, LOW, users).affectedUsersLevel()).isEqualTo(expected);
  }

  @Test
  @DisplayName("TC-39 weighted score: many affected users raise priority where the matrix does not")
  void weightedCountsUsers() {
    PriorityFactors few = f(MEDIUM, HIGH, MEDIUM, 3);
    PriorityFactors many = f(MEDIUM, HIGH, MEDIUM, 5000);
    // matrix ignores the head-count completely
    assertThat(matrix.assess(few).priority()).isEqualTo(matrix.assess(many).priority());
    // weighted: 0.35*2+0.30*3+0.20*2+0.15*1 = 2.15 (P3) vs ... +0.15*4 = 2.60 (P2)
    assertThat(weighted.assess(few).score()).isEqualTo(2.15);
    assertThat(weighted.assess(few).priority()).isEqualTo(Priority.P3);
    assertThat(weighted.assess(many).score()).isEqualTo(2.60);
    assertThat(weighted.assess(many).priority()).isEqualTo(Priority.P2);
  }

  @Test
  @DisplayName("TC-40 weighted bands at their extremes and exact boundaries")
  void weightedBands() {
    assertThat(weighted.assess(f(LOW, LOW, LOW, 1)).priority()).isEqualTo(Priority.P4); // 1.00
    assertThat(weighted.assess(f(MEDIUM, MEDIUM, LOW, 1)).score()).isEqualTo(1.65); // P4
    assertThat(weighted.assess(f(MEDIUM, MEDIUM, MEDIUM, 10)).priority()) // 2.00
        .isEqualTo(Priority.P3);
    assertThat(weighted.assess(f(HIGH, HIGH, HIGH, 100)).priority()) // 3.00
        .isEqualTo(Priority.P2);
    assertThat(weighted.assess(f(CRITICAL, CRITICAL, CRITICAL, 5000)).score()).isEqualTo(4.0);
    assertThat(weighted.assess(f(CRITICAL, CRITICAL, CRITICAL, 5000)).priority())
        .isEqualTo(Priority.P1);
  }

  @Test
  @DisplayName("TC-41 weighted strategy rejects weights that are negative or do not sum to 1")
  void weightedValidation() {
    assertThatThrownBy(() -> new WeightedScorePriorityStrategy(0.5, 0.5, 0.5, 0.0))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("add up to 1.0");
    assertThatThrownBy(() -> new WeightedScorePriorityStrategy(1.2, -0.2, 0.0, 0.0))
        .isInstanceOf(InvalidInputException.class);
  }

  @Test
  @DisplayName(
      "TC-42 incident is prioritised by whichever strategy is active; switching needs no code"
          + " change")
  void incidentUsesActiveStrategy() {
    TestData data = new TestData();
    StrategyRegistry<PriorityStrategy> registry = PriorityStrategies.defaults();
    Incident incident = data.reported(data.payment); // HIGH x HIGH x CRITICAL, 50 users
    incident.classify(data.network, data.system, TestData.T0);

    PriorityAssessment a = incident.prioritize(registry.active(), data.system, TestData.T0);
    assertThat(a.strategy()).isEqualTo("MATRIX");
    assertThat(a.priority()).isEqualTo(Priority.P1); // 36
    assertThat(incident.priority()).contains(Priority.P1);

    registry.activate("weighted");
    // 0.35*3 + 0.30*3 + 0.20*4 + 0.15*2 = 3.05 -> P2
    PriorityAssessment b = incident.prioritize(registry.active(), data.system, TestData.T0);
    assertThat(b.strategy()).isEqualTo("WEIGHTED");
    assertThat(b.priority()).isEqualTo(Priority.P2);
    assertThat(incident.priority()).contains(Priority.P2);

    assertThatThrownBy(() -> registry.activate("MAGIC"))
        .isInstanceOf(InvalidInputException.class)
        .hasMessageContaining("MATRIX");
    assertThat(registry.active().name()).isEqualTo("WEIGHTED");
  }

  @Test
  @DisplayName("TC-43 priority factors reject missing values and zero affected users")
  void factorValidation() {
    assertThatThrownBy(() -> f(null, LOW, LOW, 1)).isInstanceOf(InvalidInputException.class);
    assertThatThrownBy(() -> f(LOW, LOW, LOW, 0)).isInstanceOf(InvalidInputException.class);
  }
}
