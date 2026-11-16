package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.UserId;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/**
 * A recorded SLA event: either an early <b>warning</b> (deadline approaching) or a <b>violation</b>
 * (deadline missed). Violations are kept permanently for reporting; both kinds trigger
 * notifications.
 *
 * @param type warning or violation
 * @param incidentId the incident concerned
 * @param kind which clock (response or resolution)
 * @param priority the incident's priority when the alert was raised
 * @param dueAt the deadline concerned
 * @param detectedAt when the monitor noticed
 * @param assignee technician responsible at that moment, or {@code null} if unassigned
 */
public record SlaAlert(
    Type type,
    IncidentId incidentId,
    SlaKind kind,
    Priority priority,
    Instant dueAt,
    Instant detectedAt,
    UserId assignee) {

  /** Severity of the alert. */
  public enum Type {
    WARNING,
    VIOLATION
  }

  public SlaAlert {
    Require.notNull(type, "Alert type");
    Require.notNull(incidentId, "Incident id");
    Require.notNull(kind, "SLA kind");
    Require.notNull(priority, "Priority");
    Require.notNull(dueAt, "Due time");
    Require.notNull(detectedAt, "Detection time");
  }

  public Optional<UserId> assigneeId() {
    return Optional.ofNullable(assignee);
  }

  /** How late the work was when detected (zero for a warning). */
  public Duration overdueBy() {
    return detectedAt.isAfter(dueAt) ? Duration.between(dueAt, detectedAt) : Duration.ZERO;
  }

  /** Short text suitable for an audit entry or notification. */
  public String describe() {
    String due = DUE_FORMAT.format(dueAt);
    return switch (type) {
      case WARNING -> "%s %s SLA at risk: due %s".formatted(incidentId, kind, due);
      case VIOLATION ->
          "%s %s SLA violated: due %s, overdue by %d min"
              .formatted(incidentId, kind, due, overdueBy().toMinutes());
    };
  }

  /** "14 Nov 16:49 UTC" - readable in the audit trail and notifications. */
  private static final DateTimeFormatter DUE_FORMAT =
      DateTimeFormatter.ofPattern("d MMM HH:mm 'UTC'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
}
