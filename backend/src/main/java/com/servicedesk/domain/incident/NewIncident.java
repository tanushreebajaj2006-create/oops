package com.servicedesk.domain.incident;

import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.user.User;

/** What a user supplies when reporting a problem. Validated by {@link IncidentFactory}. */
public record NewIncident(
    User reporter,
    String title,
    String description,
    ItService service,
    Level impact,
    Level urgency,
    int affectedUsers) {}
