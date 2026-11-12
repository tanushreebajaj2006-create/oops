package com.servicedesk.domain.priority;

/**
 * Final priority of an incident. P1 is the most urgent. How a priority is computed is decided by a
 * pluggable strategy (Phase 2); this enum only names the levels.
 */
public enum Priority {
  P1("Critical"),
  P2("High"),
  P3("Medium"),
  P4("Low");

  private final String label;

  Priority(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  /** True if this priority is more urgent than {@code other}. */
  public boolean isHigherThan(Priority other) {
    return ordinal() < other.ordinal();
  }
}
