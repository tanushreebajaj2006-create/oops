package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.Set;

/**
 * Search criteria for incidents. Every filter is optional ({@code null} or empty = ignore); the
 * filters that are set are combined with AND. Built fluently:
 *
 * <pre>
 *   IncidentQuery.all().withStatuses(OPEN).withPriorities(Set.of(P1)).withService("PAYMENT")
 * </pre>
 *
 * Covers the brief's search criteria: incident id, date, status, priority, category, service,
 * technician - plus reporter and free text. (SLA status is derived from time, so it is filtered by
 * the application layer on top of this query.)
 *
 * @param incidentId exact id
 * @param statuses any of these statuses
 * @param priorities any of these priorities
 * @param categoryCode category
 * @param serviceCode affected service
 * @param assigneeId assigned technician
 * @param reporterId reporting user
 * @param createdFrom created at or after
 * @param createdTo created before
 * @param text case-insensitive match in title or description
 * @param limit maximum results (1-500)
 * @param offset results to skip, for paging
 */
public record IncidentQuery(
    IncidentId incidentId,
    Set<IncidentStatus> statuses,
    Set<Priority> priorities,
    String categoryCode,
    String serviceCode,
    UserId assigneeId,
    UserId reporterId,
    Instant createdFrom,
    Instant createdTo,
    String text,
    int limit,
    int offset) {

  public static final int DEFAULT_LIMIT = 100;
  public static final int MAX_LIMIT = 500;

  /** Statuses in which someone still has work to do. */
  public static final Set<IncidentStatus> OPEN =
      Set.of(
          IncidentStatus.NEW,
          IncidentStatus.CLASSIFIED,
          IncidentStatus.ASSIGNED,
          IncidentStatus.ACKNOWLEDGED,
          IncidentStatus.IN_PROGRESS,
          IncidentStatus.AWAITING_INFO);

  public IncidentQuery {
    statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
    categoryCode = blankToNull(categoryCode, true);
    serviceCode = blankToNull(serviceCode, true);
    text = blankToNull(text, false);
    Require.inRange(limit, 1, MAX_LIMIT, "Limit");
    Require.inRange(offset, 0, Integer.MAX_VALUE, "Offset");
    if (createdFrom != null && createdTo != null) {
      Require.that(!createdTo.isBefore(createdFrom), "Date range end is before its start");
    }
  }

  public static IncidentQuery all() {
    return new IncidentQuery(
        null, null, null, null, null, null, null, null, null, null, DEFAULT_LIMIT, 0);
  }

  public IncidentQuery withId(IncidentId id) {
    return new IncidentQuery(
        id,
        statuses,
        priorities,
        categoryCode,
        serviceCode,
        assigneeId,
        reporterId,
        createdFrom,
        createdTo,
        text,
        limit,
        offset);
  }

  public IncidentQuery withStatuses(Set<IncidentStatus> s) {
    return new IncidentQuery(
        incidentId,
        s,
        priorities,
        categoryCode,
        serviceCode,
        assigneeId,
        reporterId,
        createdFrom,
        createdTo,
        text,
        limit,
        offset);
  }

  public IncidentQuery withPriorities(Set<Priority> p) {
    return new IncidentQuery(
        incidentId,
        statuses,
        p,
        categoryCode,
        serviceCode,
        assigneeId,
        reporterId,
        createdFrom,
        createdTo,
        text,
        limit,
        offset);
  }

  public IncidentQuery withCategory(String code) {
    return new IncidentQuery(
        incidentId,
        statuses,
        priorities,
        code,
        serviceCode,
        assigneeId,
        reporterId,
        createdFrom,
        createdTo,
        text,
        limit,
        offset);
  }

  public IncidentQuery withService(String code) {
    return new IncidentQuery(
        incidentId,
        statuses,
        priorities,
        categoryCode,
        code,
        assigneeId,
        reporterId,
        createdFrom,
        createdTo,
        text,
        limit,
        offset);
  }

  public IncidentQuery assignedTo(UserId technician) {
    return new IncidentQuery(
        incidentId,
        statuses,
        priorities,
        categoryCode,
        serviceCode,
        technician,
        reporterId,
        createdFrom,
        createdTo,
        text,
        limit,
        offset);
  }

  public IncidentQuery reportedBy(UserId reporter) {
    return new IncidentQuery(
        incidentId,
        statuses,
        priorities,
        categoryCode,
        serviceCode,
        assigneeId,
        reporter,
        createdFrom,
        createdTo,
        text,
        limit,
        offset);
  }

  public IncidentQuery createdBetween(Instant from, Instant to) {
    return new IncidentQuery(
        incidentId,
        statuses,
        priorities,
        categoryCode,
        serviceCode,
        assigneeId,
        reporterId,
        from,
        to,
        text,
        limit,
        offset);
  }

  public IncidentQuery containing(String words) {
    return new IncidentQuery(
        incidentId,
        statuses,
        priorities,
        categoryCode,
        serviceCode,
        assigneeId,
        reporterId,
        createdFrom,
        createdTo,
        words,
        limit,
        offset);
  }

  public IncidentQuery page(int newLimit, int newOffset) {
    return new IncidentQuery(
        incidentId,
        statuses,
        priorities,
        categoryCode,
        serviceCode,
        assigneeId,
        reporterId,
        createdFrom,
        createdTo,
        text,
        newLimit,
        newOffset);
  }

  private static String blankToNull(String s, boolean upper) {
    if (s == null || s.isBlank()) {
      return null;
    }
    return upper ? s.strip().toUpperCase() : s.strip();
  }
}
