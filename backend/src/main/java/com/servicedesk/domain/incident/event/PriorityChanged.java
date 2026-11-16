package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.priority.Priority;
import java.util.Optional;

/** Priority was calculated for the first time, recalculated, or set manually. */
public final class PriorityChanged extends IncidentEvent {

  private final Priority previous;
  private final Priority current;
  private final String rationale;

  public PriorityChanged(
      EventHeader header, Priority previous, Priority current, String rationale) {
    super(header);
    this.previous = previous;
    this.current = Require.notNull(current, "Priority");
    this.rationale = rationale;
  }

  public Optional<Priority> previous() {
    return Optional.ofNullable(previous);
  }

  public Priority current() {
    return current;
  }

  public Optional<String> rationale() {
    return Optional.ofNullable(rationale);
  }

  /** First calculation, as opposed to a change of an existing priority. */
  public boolean isInitial() {
    return previous == null;
  }

  /** The priority actually moved (worth telling people about). */
  public boolean isChange() {
    return previous != null && previous != current;
  }

  @Override
  public EventType type() {
    return EventType.PRIORITY_CHANGED;
  }

  @Override
  public String description() {
    String what =
        previous == null
            ? "Priority calculated as " + current
            : previous == current
                ? "Priority recalculated, unchanged at " + current
                : "Priority changed from " + previous + " to " + current;
    return rationale == null ? what : what + " (" + rationale + ")";
  }
}
