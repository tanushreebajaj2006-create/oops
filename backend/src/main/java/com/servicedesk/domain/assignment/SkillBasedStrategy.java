package com.servicedesk.domain.assignment;

import com.servicedesk.domain.exception.InvalidIncidentException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.Skill;
import com.servicedesk.domain.user.Technician;
import java.util.Comparator;
import java.util.List;

/**
 * Only technicians with a skill in the incident's category are considered; the most proficient
 * wins, ties broken by lower load and then more experience.
 *
 * <p>If nobody has the skill the list is empty and the assigner reports "no suitable technician"
 * rather than silently handing a database outage to a printer specialist.
 */
public final class SkillBasedStrategy implements AssignmentStrategy {

  public static final String NAME = "SKILL_BASED";

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Most proficient technician in the incident's category; ties go to the less busy";
  }

  @Override
  public List<AssignmentCandidate> rank(Incident incident, List<Technician> eligible) {
    String category = categoryOf(incident);
    Comparator<Technician> order =
        Comparator.<Technician>comparingInt(t -> proficiency(t, category))
            .reversed()
            .thenComparingDouble(Technician::utilisation)
            .thenComparing(Comparator.comparingInt(Technician::experienceYears).reversed())
            .thenComparing(t -> t.id().value());
    return eligible.stream()
        .filter(t -> t.hasSkill(category))
        .sorted(order)
        .map(
            t ->
                new AssignmentCandidate(
                    t,
                    proficiency(t, category),
                    "%s skill %d/%d, load %d/%d"
                        .formatted(
                            category,
                            proficiency(t, category),
                            Skill.MAX,
                            t.activeWorkload(),
                            t.maxWorkload())))
        .toList();
  }

  static String categoryOf(Incident incident) {
    return incident
        .category()
        .orElseThrow(
            () -> new InvalidIncidentException(incident.id() + " must be classified first"))
        .code();
  }

  static int proficiency(Technician t, String category) {
    return t.skillFor(category).map(Skill::proficiency).orElse(0);
  }
}
