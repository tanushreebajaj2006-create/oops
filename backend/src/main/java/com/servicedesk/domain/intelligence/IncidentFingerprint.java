package com.servicedesk.domain.intelligence;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import java.time.Instant;

/**
 * What duplicate detection compares: the text, service and time of a report. Can describe a saved
 * incident or a draft the user has not submitted yet ({@code id} null), so the UI can warn "this
 * may already be reported" before a duplicate is even created.
 */
public record IncidentFingerprint(
    IncidentId id, String title, String description, String serviceCode, Instant reportedAt) {

  public IncidentFingerprint {
    Require.notNull(reportedAt, "Report time");
  }

  public static IncidentFingerprint of(Incident i) {
    return new IncidentFingerprint(
        i.id(), i.title(), i.description(), i.service().code(), i.createdAt());
  }

  public static IncidentFingerprint draft(
      String title, String description, String serviceCode, Instant now) {
    return new IncidentFingerprint(null, title, description, serviceCode, now);
  }

  public TextProfile profile() {
    return TextProfile.of(title, description);
  }
}
