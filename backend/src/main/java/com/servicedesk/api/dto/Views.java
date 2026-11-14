package com.servicedesk.api.dto;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.incident.event.EventType;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Response bodies of the REST API. Plain records built by {@link com.servicedesk.api.ApiMapper}:
 * the API never serialises domain objects directly, so internal fields (like password hashes) can
 * never leak and the domain can change without breaking clients.
 */
public final class Views {

  private Views() {}

  public record UserRef(String id, String name, Role role) {}

  public record UserView(
      String id,
      Role role,
      String fullName,
      String email,
      String phone,
      boolean active,
      String department,
      Map<String, Integer> skills,
      Integer experienceYears,
      Integer maxWorkload,
      Integer activeWorkload,
      Boolean available) {}

  public record Session(String token, String tokenType, Instant expiresAt, UserView user) {}

  public record SlaMeasureView(
      Instant dueAt,
      Instant completedAt,
      SlaStatus status,
      int percentUsed,
      long minutesRemaining) {}

  public record SlaView(
      String policy,
      Priority priority,
      SlaStatus overall,
      SlaMeasureView response,
      SlaMeasureView resolution) {}

  public record IncidentSummary(
      String id,
      String title,
      IncidentStatus status,
      Priority priority,
      String category,
      String serviceCode,
      String serviceName,
      UserRef reporter,
      UserRef assignee,
      Instant createdAt,
      Instant updatedAt,
      SlaStatus slaStatus,
      Instant resolutionDueAt,
      int escalationLevel) {}

  public record NoteView(UserRef author, NoteType type, String text, Instant createdAt) {}

  public record ResolutionView(
      String summary, String rootCause, UserRef resolvedBy, Instant resolvedAt) {}

  public record FeedbackView(int rating, String comment, Instant givenAt) {}

  public record ClosureView(UserRef closedBy, Instant closedAt, String note) {}

  public record IncidentDetail(
      String id,
      String title,
      String description,
      IncidentStatus status,
      Priority priority,
      String category,
      String serviceCode,
      String serviceName,
      Level serviceCriticality,
      Level impact,
      Level urgency,
      int affectedUsers,
      UserRef reporter,
      UserRef assignee,
      Instant createdAt,
      Instant updatedAt,
      Instant acknowledgedAt,
      Instant resolvedAt,
      int reopenCount,
      int escalationLevel,
      SlaView sla,
      List<NoteView> notes,
      ResolutionView resolution,
      FeedbackView feedback,
      ClosureView closure,
      Set<IncidentAction> allowedActions) {}

  public record PageView<T>(List<T> items, long total, int limit, int offset) {}

  public record AuditEntryView(
      long seq,
      Instant timestamp,
      String actorId,
      Role actorRole,
      String action,
      String incidentId,
      IncidentStatus previousStatus,
      IncidentStatus newStatus,
      String description) {}

  public record NotificationView(
      long id,
      String subject,
      String body,
      String incidentId,
      EventType eventType,
      boolean urgent,
      Instant createdAt,
      boolean read) {}

  public record CandidateView(
      UserRef technician, double score, String reason, int activeWorkload, int maxWorkload) {}

  public record ServiceView(String code, String name, Level criticality, boolean active) {}

  public record CategoryView(String code, String name, boolean active) {}

  public record SlaTargetView(long responseMinutes, long resolutionMinutes) {}

  public record SlaPolicyView(
      String name,
      String serviceCode,
      double warningThreshold,
      Map<Priority, SlaTargetView> targets) {}

  public record StrategyView(String name, String description) {}

  public record StrategiesView(
      StrategyView assignment,
      List<StrategyView> assignmentOptions,
      StrategyView priority,
      List<StrategyView> priorityOptions) {}

  public record TechnicianLoadView(
      UserRef technician,
      int activeIncidents,
      int maxWorkload,
      int utilisationPercent,
      boolean available,
      long resolvedTotal) {}

  public record SlaAlertView(
      String type,
      String incidentId,
      String kind,
      Priority priority,
      Instant dueAt,
      Instant detectedAt,
      String assigneeId,
      long overdueMinutes) {}

  public record SlaReportView(
      List<SlaAlertView> recentViolations,
      List<SlaSnapshotRow> atRisk,
      List<SlaSnapshotRow> breachedOpen) {}

  public record SlaSnapshotRow(
      String incidentId, Priority priority, SlaStatus overall, SlaView sla) {}

  public record MyWorkload(
      UserRef technician,
      int activeIncidents,
      int maxWorkload,
      boolean available,
      List<IncidentSummary> incidents) {}

  public record Count(long count) {}

  // ---- intelligent features ------------------------------------------------------------

  public record CategorySuggestionView(
      String code,
      String name,
      int confidencePercent,
      List<String> matchedTerms,
      boolean confident) {}

  /** A possibly related incident. Deliberately omits who reported it. */
  public record RelatedIncidentView(
      String id,
      String title,
      IncidentStatus status,
      Priority priority,
      String serviceCode,
      Instant createdAt,
      int scorePercent,
      String verdict,
      List<String> reasons) {}

  public record ResolutionSuggestionView(
      String sourceIncidentId,
      String sourceTitle,
      String summary,
      String rootCause,
      Integer rating,
      Instant resolvedAt,
      int scorePercent) {}

  public record SlaRiskView(
      IncidentSummary incident,
      String level,
      int projectedPercentOfAllowance,
      Instant projectedResolution,
      Instant dueAt,
      List<String> reasons) {}

  public record ClusterView(
      String pattern,
      String key,
      String summary,
      Priority highestPriority,
      int count,
      Instant firstReportedAt,
      Instant lastReportedAt,
      Set<String> commonTerms,
      List<IncidentSummary> incidents) {}
}
