package com.servicedesk.domain.assignment;

import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.Technician;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hands incidents out in turn: technicians are ordered by id, and the next incident goes to the
 * first technician after the one who received the previous incident (wrapping around).
 *
 * <p>The only <i>stateful</i> strategy: it remembers the last id in an {@link AtomicReference} so
 * concurrent assignments stay safe. Technicians who are busy or unavailable are skipped because
 * they never reach the eligible list.
 */
public final class RoundRobinStrategy implements AssignmentStrategy {

  public static final String NAME = "ROUND_ROBIN";

  private final AtomicReference<String> lastAssignedId = new AtomicReference<>();

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public String description() {
    return "Takes turns through the team in a fixed order, skipping anyone unavailable";
  }

  @Override
  public List<AssignmentCandidate> rank(Incident incident, List<Technician> eligible) {
    List<Technician> ordered =
        eligible.stream().sorted(Comparator.comparing(t -> t.id().value())).toList();
    String last = lastAssignedId.get();
    int start = 0;
    if (last != null) {
      start = ordered.size(); // default: wrap to the beginning
      for (int i = 0; i < ordered.size(); i++) {
        if (ordered.get(i).id().value().compareTo(last) > 0) {
          start = i;
          break;
        }
      }
    }
    List<AssignmentCandidate> result = new ArrayList<>(ordered.size());
    for (int k = 0; k < ordered.size(); k++) {
      Technician t = ordered.get((start + k) % ordered.size());
      result.add(
          new AssignmentCandidate(
              t, ordered.size() - k, "Next in rotation (turn " + (k + 1) + ")"));
    }
    return result;
  }

  @Override
  public void onAssigned(Technician chosen) {
    lastAssignedId.set(chosen.id().value());
  }
}
