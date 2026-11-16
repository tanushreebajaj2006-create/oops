package com.servicedesk.domain.priority;

import com.servicedesk.domain.common.StrategyRegistry;

/** Builds the registry of built-in priority strategies. */
public final class PriorityStrategies {

  private PriorityStrategies() {}

  /** Both built-in strategies, with the brief's {@link MatrixPriorityStrategy} active. */
  public static StrategyRegistry<PriorityStrategy> defaults() {
    return new StrategyRegistry<PriorityStrategy>("Priority strategy", new MatrixPriorityStrategy())
        .register(new WeightedScorePriorityStrategy());
  }
}
