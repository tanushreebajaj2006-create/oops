package com.servicedesk.domain.incident;

import com.servicedesk.domain.user.User;
import java.time.Instant;

/** Who closed (or cancelled) an incident, when, and why. */
public record Closure(User closedBy, Instant closedAt, String note) {}
