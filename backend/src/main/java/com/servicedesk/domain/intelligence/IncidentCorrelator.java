package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Spots open incidents that are probably symptoms of one cause (brief: "Incident Correlation").
 *
 * <ul>
 *   <li><b>Service outage</b>: {@value #MIN_SERVICE_GROUP} or more open incidents on the same
 *       service reported within {@link #WINDOW} of each other.
 *   <li><b>Shared component</b>: {@value #MIN_COMPONENT_GROUP} or more open incidents in the same
 *       category across at least two different services within the window - e.g. login failures on
 *       the HR portal, e-mail and VPN all pointing at single sign-on.
 * </ul>
 *
 * A dashboard can then show "possible major incident" so one team works the cause instead of three
 * technicians chasing three symptoms.
 */
public final class IncidentCorrelator {

  static final Duration WINDOW = Duration.ofHours(2);
  static final int MIN_SERVICE_GROUP = 2;
  static final int MIN_COMPONENT_GROUP = 3;

  public List<IncidentCluster> correlate(Collection<Incident> openIncidents) {
    Require.notNull(openIncidents, "Incidents");
    List<Incident> open =
        openIncidents.stream()
            .filter(Incident::isOpen)
            .sorted(Comparator.comparing(Incident::createdAt))
            .toList();
    List<IncidentCluster> clusters = new ArrayList<>();

    for (Map.Entry<String, List<Incident>> e : group(open, i -> i.service().code()).entrySet()) {
      for (List<Incident> burst : bursts(e.getValue())) {
        if (burst.size() >= MIN_SERVICE_GROUP) {
          clusters.add(
              cluster(
                  IncidentCluster.Pattern.SERVICE_OUTAGE,
                  e.getKey(),
                  burst,
                  "%d open incidents on %s within %d min - %s"
                      .formatted(
                          burst.size(),
                          burst.get(0).service().name(),
                          span(burst),
                          burst.size() >= 3 ? "possible major incident" : "may share a cause")));
        }
      }
    }
    Map<String, List<Incident>> byCategory =
        group(
            open.stream().filter(i -> i.category().isPresent()).toList(),
            i -> i.category().map(Category::code).orElseThrow());
    for (Map.Entry<String, List<Incident>> e : byCategory.entrySet()) {
      for (List<Incident> burst : bursts(e.getValue())) {
        long services = burst.stream().map(i -> i.service().code()).distinct().count();
        if (burst.size() >= MIN_COMPONENT_GROUP && services >= 2) {
          clusters.add(
              cluster(
                  IncidentCluster.Pattern.SHARED_COMPONENT,
                  e.getKey(),
                  burst,
                  "%d %s incidents across %d services within %d min - check shared infrastructure"
                      .formatted(burst.size(), e.getKey(), services, span(burst))));
        }
      }
    }
    clusters.sort(
        Comparator.comparing(
                (IncidentCluster c) ->
                    c.highestPriority() == null ? Priority.P4 : c.highestPriority())
            .thenComparing(c -> -c.incidents().size()));
    return clusters;
  }

  private static Map<String, List<Incident>> group(
      List<Incident> incidents, Function<Incident, String> key) {
    Map<String, List<Incident>> g = new LinkedHashMap<>();
    for (Incident i : incidents) {
      g.computeIfAbsent(key.apply(i), k -> new ArrayList<>()).add(i);
    }
    return g;
  }

  /** Splits a time-ordered list wherever two consecutive reports are more than WINDOW apart. */
  private static List<List<Incident>> bursts(List<Incident> ordered) {
    List<List<Incident>> out = new ArrayList<>();
    List<Incident> current = new ArrayList<>();
    for (Incident i : ordered) {
      if (!current.isEmpty()
          && Duration.between(current.get(current.size() - 1).createdAt(), i.createdAt())
                  .compareTo(WINDOW)
              > 0) {
        out.add(current);
        current = new ArrayList<>();
      }
      current.add(i);
    }
    if (!current.isEmpty()) {
      out.add(current);
    }
    return out;
  }

  private static IncidentCluster cluster(
      IncidentCluster.Pattern pattern, String key, List<Incident> incidents, String summary) {
    Priority highest =
        incidents.stream()
            .flatMap(i -> i.priority().stream())
            .min(Comparator.naturalOrder())
            .orElse(null);
    return new IncidentCluster(pattern, key, incidents, highest, commonTerms(incidents), summary);
  }

  /** Terms appearing in at least half of the reports. */
  private static Set<String> commonTerms(List<Incident> incidents) {
    Map<String, Integer> docs = new HashMap<>();
    for (Incident i : incidents) {
      for (String t : new TreeSet<>(TextAnalyzer.terms(i.title() + " " + i.description()))) {
        docs.merge(t, 1, Integer::sum);
      }
    }
    Map<Integer, List<String>> byCount = new TreeMap<>(Comparator.reverseOrder());
    docs.forEach((t, n) -> byCount.computeIfAbsent(n, k -> new ArrayList<>()).add(t));
    Set<String> out = new TreeSet<>();
    int needed = Math.max(2, (incidents.size() + 1) / 2);
    byCount.forEach(
        (n, terms) -> {
          if (n >= needed && out.size() < 6) {
            terms.stream().sorted().limit(6 - out.size()).forEach(out::add);
          }
        });
    return out;
  }

  private static long span(List<Incident> burst) {
    return Math.max(
        1,
        Duration.between(burst.get(0).createdAt(), burst.get(burst.size() - 1).createdAt())
            .toMinutes());
  }
}
