package com.servicedesk.domain.assignment;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.common.StrategyRegistry;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.exception.WorkloadExceededException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.intelligence.IncidentHistory;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Domain service that recommends and performs technician assignment using whichever {@link
 * AssignmentStrategy} is currently active in the {@link StrategyRegistry}.
 *
 * <p>Responsibilities are split cleanly: the assigner decides who is <i>eligible</i> (a business
 * rule shared by all strategies), the strategy decides the <i>order</i>, and {@link
 * Incident#assignTo} enforces the lifecycle and workload rules. Adding a fifth strategy touches
 * none of this class.
 */
public class TechnicianAssigner {

  private final StrategyRegistry<AssignmentStrategy> strategies;

  public TechnicianAssigner(StrategyRegistry<AssignmentStrategy> strategies) {
    this.strategies = Require.notNull(strategies, "Assignment strategies");
  }

  /** Registry with the four rule-based strategies, {@link SkillBasedStrategy} active. */
  public static StrategyRegistry<AssignmentStrategy> defaultStrategies() {
    return new StrategyRegistry<AssignmentStrategy>("Assignment strategy", new SkillBasedStrategy())
        .register(new LoadBasedStrategy())
        .register(new RoundRobinStrategy())
        .register(new PriorityBasedStrategy());
  }

  /** All built-in strategies including the history-aware {@link SmartAssignmentStrategy}. */
  public static StrategyRegistry<AssignmentStrategy> defaultStrategies(
      Supplier<IncidentHistory> history) {
    return defaultStrategies().register(new SmartAssignmentStrategy(history));
  }

  /**
   * Ranked suggestions using the active strategy, without assigning anything. Used by the UI so a
   * team lead can see the recommendation and override it.
   */
  public List<AssignmentCandidate> recommend(Incident incident, Collection<Technician> team) {
    return strategies.active().rank(incident, eligible(incident, team));
  }

  /**
   * Assigns the incident to the best candidate according to the active strategy.
   *
   * <p>If the top candidate turns out to be unable to take the work (e.g. someone else just gave
   * them an incident), the next candidate is tried.
   *
   * @throws TechnicianUnavailableException if no eligible technician can take the incident
   */
  public AssignmentDecision assign(
      Incident incident, Collection<Technician> team, User actor, Instant at) {
    Require.notNull(incident, "Incident");
    AssignmentStrategy strategy = strategies.active();
    List<AssignmentCandidate> ranked = strategy.rank(incident, eligible(incident, team));
    for (AssignmentCandidate candidate : ranked) {
      try {
        incident.assignTo(
            candidate.technician(), actor, at, strategy.name() + ": " + candidate.reason());
      } catch (TechnicianUnavailableException | WorkloadExceededException e) {
        continue;
      }
      strategy.onAssigned(candidate.technician());
      return new AssignmentDecision(
          incident.id(),
          candidate.technician(),
          strategy.name(),
          candidate.reason(),
          ranked.size());
    }
    throw new TechnicianUnavailableException(
        "No available technician for "
            + incident.id()
            + " using "
            + strategy.name()
            + " ("
            + ranked.size()
            + " candidates)");
  }

  public AssignmentStrategy activeStrategy() {
    return strategies.active();
  }

  /** Active, available, below their workload limit, and not already holding this incident. */
  static List<Technician> eligible(Incident incident, Collection<Technician> team) {
    Require.notNull(team, "Technician list");
    Technician current = incident.assignee().orElse(null);
    return team.stream()
        .filter(Technician::canTakeMoreWork)
        .filter(t -> !t.equals(current))
        .toList();
  }
}
