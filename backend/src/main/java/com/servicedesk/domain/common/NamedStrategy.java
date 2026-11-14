package com.servicedesk.domain.common;

/**
 * Common contract for every interchangeable algorithm in the system (priority calculation,
 * technician assignment, ...).
 *
 * <p>The stable {@link #name()} is what an administrator picks in the UI and what is stored in
 * configuration, so the active algorithm can be switched at runtime through a {@link
 * StrategyRegistry} without touching any calling code.
 */
public interface NamedStrategy {

  /** Stable, upper-case key such as {@code LOAD_BASED}. */
  String name();

  /** One-line, human-readable explanation shown to administrators. */
  String description();
}
