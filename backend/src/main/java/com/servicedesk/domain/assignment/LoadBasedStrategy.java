package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.Technician;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the technician with the most spare capacity, ignoring skills.
 *
 * <p>Order: lowest utilisation (open / max), then fewest open incidents, then id for a
 * deterministic result. Good for keeping queues even when everyone is a generalist.
 */
public final class LoadBasedStrategy implements AssignmentStrategy {

  public static final String NAME = "LOAD_BASED";

  static final Comparator<Technician> LEAST_LOADED =
      Comparator.comparingDouble(Technician::utilisation)
          .thenComparingInt(Technician::activeWorkload)
          .thenComparing(t -> t.id().value());

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Technician with the most spare capacity (lowest open/max ratio)";
  }

  @Override
  public List<AssignmentCandidate> rank(Incident incident, List<Technician> eligible) {
    return eligible.stream()
        .sorted(LEAST_LOADED)
        .map(
            t ->
                new AssignmentCandidate(
                    t,
                    1.0 - t.utilisation(),
                    "Load %d/%d".formatted(t.activeWorkload(), t.maxWorkload())))
        .toList();
  }
}
