package com.servicedesk.domain.priority;

/**
 * Four-step scale shared by impact, urgency and business criticality. The numeric {@link #weight()}
 * feeds the priority formula {@code Impact x Urgency x Criticality}.
 */
public enum Level {
  LOW(1),
  MEDIUM(2),
  HIGH(3),
  CRITICAL(4);

  private final int weight;

  Level(int weight) {
    this.weight = weight;
  }

  public int weight() {
    return weight;
  }
}
