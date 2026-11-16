package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Technician;
import java.util.Comparator;
import java.util.List;

/**
 * Treats urgent and routine work differently.
 *
 * <ul>
 *   <li><b>P1 / P2</b>: the most capable person - highest skill in the category, then most
 *       experience, then lowest load. Speed of a correct fix matters more than fairness.
 *   <li><b>P3 / P4</b>: delegates to {@link LoadBasedStrategy} among those with the skill, which
 *       spreads routine work and keeps senior staff free for emergencies.
 * </ul>
 *
 * In both cases technicians with the right skill are preferred, but if nobody has it the strategy
 * falls back to the whole eligible list so an urgent incident is never left unassigned.
 *
 * <p>Shows <b>composition</b> of strategies: it re-uses another strategy object instead of copying
 * its logic.
 */
public final class PriorityBasedStrategy implements AssignmentStrategy {

  public static final String NAME = "PRIORITY_BASED";

  private final AssignmentStrategy routine = new LoadBasedStrategy();

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "P1/P2 go to the most skilled and experienced; P3/P4 are spread by workload";
  }

  @Override
  public List<AssignmentCandidate> rank(Incident incident, List<Technician> eligible) {
    String category = SkillBasedStrategy.categoryOf(incident);
    List<Technician> skilled = eligible.stream().filter(t -> t.hasSkill(category)).toList();
    List<Technician> pool = skilled.isEmpty() ? eligible : skilled;
    String poolNote = skilled.isEmpty() ? " (no " + category + " specialist free)" : "";

    Priority p = incident.priority().orElse(Priority.P4);
    if (p == Priority.P1 || p == Priority.P2) {
      Comparator<Technician> expert =
          Comparator.<Technician>comparingInt(t -> SkillBasedStrategy.proficiency(t, category))
              .thenComparingInt(Technician::experienceYears)
              .reversed()
              .thenComparing(LoadBasedStrategy.LEAST_LOADED);
      return pool.stream()
          .sorted(expert)
          .map(
              t ->
                  new AssignmentCandidate(
                      t,
                      SkillBasedStrategy.proficiency(t, category) * 10 + t.experienceYears(),
                      "%s urgent: skill %d, %d yrs experience%s"
                          .formatted(
                              p,
                              SkillBasedStrategy.proficiency(t, category),
                              t.experienceYears(),
                              poolNote)))
          .toList();
    }
    return routine.rank(incident, pool).stream()
        .map(
            c ->
                new AssignmentCandidate(
                    c.technician(), c.score(), p + " routine: " + c.reason() + poolNote))
        .toList();
  }
}
