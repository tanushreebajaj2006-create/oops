package com.servicedesk.domain.common;

import com.servicedesk.domain.exception.InvalidInputException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds every available implementation of one kind of strategy and remembers which one is active.
 *
 * <p>This is the <b>context</b> side of the <b>Strategy pattern</b>, written once with
 * <b>generics</b> and reused for priority calculation and technician assignment. Callers ask for
 * {@link #active()} and never name a concrete class, so an administrator can switch algorithms at
 * runtime ({@link #activate(String)}) and a developer can add a new one by registering it - no
 * other code changes.
 *
 * <p>Strategies are kept in a {@link LinkedHashMap} (O(1) lookup by name, stable display order).
 * The active strategy is {@code volatile} so a switch made by an admin request is immediately seen
 * by background jobs on other threads.
 *
 * @param <S> the strategy interface, e.g. {@code AssignmentStrategy}
 */
public final class StrategyRegistry<S extends NamedStrategy> {

  private final String kind;
  private final Map<String, S> strategies = new LinkedHashMap<>();
  private volatile S active;

  /**
   * @param kind what these strategies do, used in error messages (e.g. "Assignment strategy")
   * @param defaultStrategy registered and made active immediately, so {@link #active()} never
   *     returns null
   */
  public StrategyRegistry(String kind, S defaultStrategy) {
    this.kind = Require.notBlank(kind, "Strategy kind");
    register(Require.notNull(defaultStrategy, "Default " + kind));
    this.active = defaultStrategy;
  }

  /** Adds (or replaces) a strategy under its own name. Returns {@code this} for chaining. */
  public synchronized StrategyRegistry<S> register(S strategy) {
    Require.notNull(strategy, kind);
    String key = Require.notBlank(strategy.name(), kind + " name").toUpperCase();
    strategies.put(key, strategy);
    if (active != null && active.name().equalsIgnoreCase(key)) {
      active = strategy;
    }
    return this;
  }

  /**
   * Makes the named strategy the active one.
   *
   * @throws InvalidInputException if no strategy with that name is registered
   */
  public synchronized S activate(String name) {
    S chosen = get(name);
    this.active = chosen;
    return chosen;
  }

  /**
   * @throws InvalidInputException if no strategy with that name is registered
   */
  public synchronized S get(String name) {
    String key = Require.notBlank(name, kind + " name").toUpperCase();
    S found = strategies.get(key);
    if (found == null) {
      throw new InvalidInputException(
          "Unknown " + kind.toLowerCase() + " '" + name + "'. Available: " + strategies.keySet());
    }
    return found;
  }

  public S active() {
    return active;
  }

  public synchronized Collection<S> available() {
    return Collections.unmodifiableList(new ArrayList<>(strategies.values()));
  }
}
