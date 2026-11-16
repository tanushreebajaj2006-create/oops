package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import java.time.Instant;

/** The reporter's rating (1-5 stars) of how their incident was handled. */
public record Feedback(int rating, String comment, Instant givenAt) {

  public Feedback {
    Require.inRange(rating, 1, 5, "Rating");
    comment = Require.maxLength(comment, 1000, "Feedback comment");
    Require.notNull(givenAt, "Feedback time");
  }
}
