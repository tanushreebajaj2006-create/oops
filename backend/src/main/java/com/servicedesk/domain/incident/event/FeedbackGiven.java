package com.servicedesk.domain.incident.event;

/** The reporter rated the service after resolution. */
public final class FeedbackGiven extends IncidentEvent {

  private final int rating;

  public FeedbackGiven(EventHeader header, int rating) {
    super(header);
    this.rating = rating;
  }

  public int rating() {
    return rating;
  }

  @Override
  public EventType type() {
    return EventType.FEEDBACK_GIVEN;
  }

  @Override
  public String description() {
    return "Reporter rated the resolution " + rating + "/5";
  }
}
