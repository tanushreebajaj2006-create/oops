package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.User;
import java.time.Instant;

/**
 * How an incident was fixed. Kept for the reporter and reused later to suggest resolutions for
 * similar incidents.
 */
public record Resolution(String summary, String rootCause, User resolvedBy, Instant resolvedAt) {

  public Resolution {
    summary =
        Require.maxLength(Require.notBlank(summary, "Resolution summary"), 2000, "Resolution");
    rootCause = Require.maxLength(rootCause, 2000, "Root cause");
    Require.notNull(resolvedBy, "Resolved by");
    Require.notNull(resolvedAt, "Resolved at");
  }
}
