package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import java.util.Optional;

/** The incident was given (or moved to) a category. */
public final class IncidentClassified extends IncidentEvent {

  private final String categoryCode;
  private final String previousCategoryCode;

  public IncidentClassified(EventHeader header, String categoryCode, String previousCategoryCode) {
    super(header);
    this.categoryCode = Require.notBlank(categoryCode, "Category code");
    this.previousCategoryCode = previousCategoryCode;
  }

  public String categoryCode() {
    return categoryCode;
  }

  public Optional<String> previousCategoryCode() {
    return Optional.ofNullable(previousCategoryCode);
  }

  @Override
  public EventType type() {
    return EventType.CLASSIFIED;
  }

  @Override
  public String description() {
    return previousCategoryCode == null
        ? "Incident classified as " + categoryCode
        : "Category changed from " + previousCategoryCode + " to " + categoryCode;
  }
}
