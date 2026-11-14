package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import java.util.Optional;

/** The incident was given (or moved to) a category. */
public final class IncidentClassified extends IncidentEvent {

  private final String categoryCode;
  private final String previousCategoryCode;
  private final String reason;

  public IncidentClassified(
      EventHeader header, String categoryCode, String previousCategoryCode, String reason) {
    super(header);
    this.categoryCode = Require.notBlank(categoryCode, "Category code");
    this.previousCategoryCode = previousCategoryCode;
    this.reason = reason == null || reason.isBlank() ? null : reason.strip();
  }

  public String categoryCode() {
    return categoryCode;
  }

  public Optional<String> previousCategoryCode() {
    return Optional.ofNullable(previousCategoryCode);
  }

  /** Why this category was chosen, e.g. the auto-categoriser's confidence. */
  public Optional<String> reason() {
    return Optional.ofNullable(reason);
  }

  @Override
  public EventType type() {
    return EventType.CLASSIFIED;
  }

  @Override
  public String description() {
    String what =
        previousCategoryCode == null
            ? "Incident classified as " + categoryCode
            : "Category changed from " + previousCategoryCode + " to " + categoryCode;
    return reason == null ? what : what + " (" + reason + ")";
  }
}
