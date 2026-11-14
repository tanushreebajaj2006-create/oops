package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * What the organisation has learned from incidents it already resolved: who fixed what, how long
 * fixes usually take, and how satisfied reporters were. Built once from a list of resolved
 * incidents and then read by the smart assignment strategy, the SLA risk predictor and the
 * resolution suggester.
 *
 * <p>Immutable snapshot; indexes are {@link HashMap}s keyed by technician, category and priority so
 * every question is answered in constant time.
 */
public final class IncidentHistory {

  private final List<Incident> resolved;
  private final Map<String, Integer> byTechCategory = new HashMap<>();
  private final Map<String, Integer> byTechService = new HashMap<>();
  private final Map<UserId, List<Integer>> ratingsByTech = new HashMap<>();
  private final Map<String, List<Duration>> durations = new HashMap<>();

  private IncidentHistory(List<Incident> resolved) {
    this.resolved = List.copyOf(resolved);
    for (Incident i : this.resolved) {
      String category = i.category().map(Category::code).orElse("-");
      Optional<Technician> tech = i.assignee();
      tech.ifPresent(
          t -> {
            byTechCategory.merge(t.id().value() + "|" + category, 1, Integer::sum);
            byTechService.merge(t.id().value() + "|" + i.service().code(), 1, Integer::sum);
            i.feedback()
                .ifPresent(
                    f ->
                        ratingsByTech
                            .computeIfAbsent(t.id(), k -> new ArrayList<>())
                            .add(f.rating()));
          });
      i.resolvedAt()
          .ifPresent(
              r -> {
                Duration d = Duration.between(i.createdAt(), r);
                String p = i.priority().map(Enum::name).orElse("-");
                durations.computeIfAbsent(category + "|" + p, k -> new ArrayList<>()).add(d);
                durations.computeIfAbsent("*|" + p, k -> new ArrayList<>()).add(d);
              });
    }
  }

  /** History built from incidents that have been resolved (others are ignored). */
  public static IncidentHistory of(Collection<Incident> incidents) {
    return new IncidentHistory(incidents.stream().filter(i -> i.resolvedAt().isPresent()).toList());
  }

  public static IncidentHistory empty() {
    return new IncidentHistory(List.of());
  }

  public List<Incident> resolvedIncidents() {
    return resolved;
  }

  public int resolvedInCategory(UserId technician, String categoryCode) {
    return byTechCategory.getOrDefault(technician.value() + "|" + categoryCode, 0);
  }

  public int resolvedOnService(UserId technician, String serviceCode) {
    return byTechService.getOrDefault(technician.value() + "|" + serviceCode, 0);
  }

  public OptionalDouble averageRating(UserId technician) {
    return ratingsByTech.getOrDefault(technician, List.of()).stream()
        .mapToInt(Integer::intValue)
        .average();
  }

  /**
   * Median time from report to resolution for this category and priority; falls back to the
   * priority alone when the category has fewer than 2 examples. The median (not the mean) so one
   * incident that sat open over a weekend does not distort the estimate.
   */
  public Optional<Duration> typicalResolutionTime(String categoryCode, Priority priority) {
    String p = priority == null ? "-" : priority.name();
    List<Duration> specific = durations.getOrDefault(categoryCode + "|" + p, List.of());
    if (specific.size() >= 2) {
      return Optional.of(median(specific));
    }
    List<Duration> general = durations.getOrDefault("*|" + p, List.of());
    return general.isEmpty() ? Optional.empty() : Optional.of(median(general));
  }

  private static Duration median(List<Duration> values) {
    List<Duration> sorted = values.stream().sorted().toList();
    int n = sorted.size();
    return n % 2 == 1
        ? sorted.get(n / 2)
        : sorted.get(n / 2 - 1).plus(sorted.get(n / 2)).dividedBy(2);
  }
}
