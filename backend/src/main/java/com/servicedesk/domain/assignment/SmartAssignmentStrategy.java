package com.servicedesk.domain.assignment;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.intelligence.IncidentHistory;
import com.servicedesk.domain.user.Technician;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Smart technician recommendation (brief: "Recommend a technician based on skills, workload,
 * experience and incident characteristics"). Every eligible technician gets a 0-1 score:
 *
 * <pre>
 *   0.35 x skill in the incident's category      (proficiency / 5)
 *   0.25 x spare capacity                         (1 - open / max)
 *   0.20 x track record on this kind of incident  (past fixes in category + 2 x on this service, capped at 10)
 *   0.10 x years of experience                    (capped at 15)
 *   0.10 x reporter satisfaction                  (average rating, neutral 3/5 if none)
 * </pre>
 *
 * For P1 incidents capacity matters less and expertise more (skill 0.45, capacity 0.15): an expert
 * with two other tickets usually beats an idle beginner in an outage.
 *
 * <p>Unlike the simple strategies it learns: the track-record and satisfaction terms come from
 * {@link IncidentHistory}, supplied lazily so the strategy always sees recent results. Technicians
 * with neither the skill nor any history in the category are only considered if nobody else is
 * free.
 */
public final class SmartAssignmentStrategy implements AssignmentStrategy {

  public static final String NAME = "SMART";

  private final Supplier<IncidentHistory> history;

  public SmartAssignmentStrategy(Supplier<IncidentHistory> history) {
    this.history = Require.notNull(history, "History supplier");
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Weighs skill, spare capacity, past fixes of similar incidents, experience and"
        + " feedback ratings";
  }

  @Override
  public List<AssignmentCandidate> rank(Incident incident, List<Technician> eligible) {
    String category = SkillBasedStrategy.categoryOf(incident);
    String service = incident.service().code();
    boolean critical = incident.priority().map(p -> p.name().equals("P1")).orElse(false);
    double wSkill = critical ? 0.45 : 0.35;
    double wCapacity = critical ? 0.15 : 0.25;
    IncidentHistory h = history.get();

    List<Technician> qualified =
        eligible.stream()
            .filter(t -> t.hasSkill(category) || h.resolvedInCategory(t.id(), category) > 0)
            .toList();
    List<Technician> pool = qualified.isEmpty() ? eligible : qualified;

    return pool.stream()
        .map(
            t -> {
              int skill = SkillBasedStrategy.proficiency(t, category);
              int inCategory = h.resolvedInCategory(t.id(), category);
              int onService = h.resolvedOnService(t.id(), service);
              OptionalDouble rating = h.averageRating(t.id());
              double score =
                  wSkill * skill / 5.0
                      + wCapacity * Math.max(0, 1 - t.utilisation())
                      + 0.20 * Math.min(1.0, (inCategory + 2.0 * onService) / 10.0)
                      + 0.10 * Math.min(t.experienceYears(), 15) / 15.0
                      + 0.10 * (rating.orElse(3.0) - 1) / 4.0;
              String reason =
                  String.format(
                      Locale.ROOT,
                      "score %.2f: skill %d/5, load %d/%d, %d similar fixed (%d on %s), %d yrs%s",
                      score,
                      skill,
                      t.activeWorkload(),
                      t.maxWorkload(),
                      inCategory,
                      onService,
                      service,
                      t.experienceYears(),
                      rating.isPresent()
                          ? String.format(Locale.ROOT, ", rated %.1f", rating.getAsDouble())
                          : "");
              return new AssignmentCandidate(t, Math.round(score * 1000) / 1000.0, reason);
            })
        .sorted(
            Comparator.comparingDouble(AssignmentCandidate::score)
                .reversed()
                .thenComparing(c -> c.technician().id().value()))
        .toList();
  }
}
