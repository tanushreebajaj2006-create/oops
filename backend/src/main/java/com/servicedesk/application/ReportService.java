package com.servicedesk.application;

import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaAlert;
import com.servicedesk.domain.sla.SlaAlertLog;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaSnapshot;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dashboards and reports for team leads and administrators: incident overview, technician workload,
 * service analysis, breakdowns by priority/category/status and SLA performance.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

  static final Set<IncidentStatus> RESOLVED =
      Set.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final ItServiceRepository services;
  private final CategoryRepository categories;
  private final SlaAlertLog slaAlerts;
  private final SlaService sla;

  public ReportService(
      IncidentRepository incidents,
      UserRepository users,
      ItServiceRepository services,
      CategoryRepository categories,
      SlaAlertLog slaAlerts,
      SlaService sla) {
    this.incidents = incidents;
    this.users = users;
    this.services = services;
    this.categories = categories;
    this.slaAlerts = slaAlerts;
    this.sla = sla;
  }

  /** Headline numbers for the dashboard. */
  public record Overview(
      long total,
      long open,
      long criticalOpen,
      long resolved,
      long cancelled,
      long slaViolations,
      long atRiskNow,
      Double slaCompliancePercent,
      Double averageResolutionMinutes,
      Double averageFeedbackRating,
      Map<Priority, Long> byPriority,
      Map<IncidentStatus, Long> byStatus) {}

  /** One technician's current load and track record. */
  public record TechnicianLoad(
      Technician technician, int activeIncidents, int maxWorkload, long resolvedTotal) {}

  /** Incidents per service (or category). */
  public record Breakdown(String code, String name, long total, long open, long critical) {}

  /** SLA view: recorded violations and the open incidents that are at risk right now. */
  public record SlaReport(
      List<SlaAlert> violations, List<SlaSnapshot> atRisk, List<SlaSnapshot> breachedOpen) {}

  public Overview overview(UserId viewerId) {
    requireReports(viewerId);
    IncidentQuery all = IncidentQuery.all();
    Map<IncidentStatus, Long> byStatus = new EnumMap<>(IncidentStatus.class);
    for (IncidentStatus s : IncidentStatus.values()) {
      byStatus.put(s, incidents.count(all.withStatuses(Set.of(s))));
    }
    Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
    for (Priority p : Priority.values()) {
      byPriority.put(p, incidents.count(all.withPriorities(Set.of(p))));
    }

    SlaCalculator calculator = sla.calculator();
    List<Incident> resolvedList =
        incidents.search(all.withStatuses(RESOLVED).page(IncidentQuery.MAX_LIMIT, 0));
    List<SlaSnapshot> resolvedSla =
        resolvedList.stream()
            .map(i -> SlaService.snapshot(i, calculator))
            .flatMap(Optional::stream)
            .toList();
    Double compliance =
        resolvedSla.isEmpty()
            ? null
            : round(
                100.0
                    * resolvedSla.stream()
                        .filter(s -> s.resolution().status() == SlaStatus.MET)
                        .count()
                    / resolvedSla.size());
    OptionalDouble mttr =
        resolvedList.stream()
            .filter(i -> i.resolvedAt().isPresent())
            .mapToLong(i -> Duration.between(i.createdAt(), i.resolvedAt().get()).toMinutes())
            .average();
    OptionalDouble rating =
        resolvedList.stream()
            .flatMap(i -> i.feedback().stream())
            .mapToInt(f -> f.rating())
            .average();
    long atRisk =
        incidents.findOpen().stream()
            .map(i -> SlaService.snapshot(i, calculator))
            .flatMap(Optional::stream)
            .filter(s -> s.overall() == SlaStatus.AT_RISK)
            .count();

    long open = incidents.count(all.withStatuses(IncidentQuery.OPEN));
    return new Overview(
        incidents.count(all),
        open,
        incidents.count(all.withStatuses(IncidentQuery.OPEN).withPriorities(Set.of(Priority.P1))),
        byStatus.get(IncidentStatus.RESOLVED)
            + byStatus.get(IncidentStatus.VERIFIED)
            + byStatus.get(IncidentStatus.CLOSED),
        byStatus.get(IncidentStatus.CANCELLED),
        slaAlerts.violations().size(),
        atRisk,
        compliance,
        mttr.isPresent() ? round(mttr.getAsDouble()) : null,
        rating.isPresent() ? round(rating.getAsDouble()) : null,
        byPriority,
        byStatus);
  }

  public List<TechnicianLoad> technicianWorkload(UserId viewerId) {
    requireReports(viewerId);
    return users.findTechnicians().stream()
        .map(
            t ->
                new TechnicianLoad(
                    t,
                    t.activeWorkload(),
                    t.maxWorkload(),
                    incidents.count(IncidentQuery.all().assignedTo(t.id()).withStatuses(RESOLVED))))
        .sorted(Comparator.comparingInt(TechnicianLoad::activeIncidents).reversed())
        .toList();
  }

  public List<Breakdown> byService(UserId viewerId) {
    requireReports(viewerId);
    return services.findAll().stream()
        .map(s -> breakdown(s.code(), s.name(), IncidentQuery.all().withService(s.code())))
        .sorted(Comparator.comparingLong(Breakdown::total).reversed())
        .toList();
  }

  public List<Breakdown> byCategory(UserId viewerId) {
    requireReports(viewerId);
    return categories.findAll().stream()
        .map(c -> breakdown(c.code(), c.name(), IncidentQuery.all().withCategory(c.code())))
        .sorted(Comparator.comparingLong(Breakdown::total).reversed())
        .toList();
  }

  public SlaReport slaReport(UserId viewerId) {
    requireReports(viewerId);
    SlaCalculator calculator = sla.calculator();
    List<SlaSnapshot> open =
        incidents.findOpen().stream()
            .map(i -> SlaService.snapshot(i, calculator))
            .flatMap(Optional::stream)
            .toList();
    List<SlaAlert> violations = slaAlerts.violations();
    return new SlaReport(
        violations.subList(Math.max(0, violations.size() - 100), violations.size()).reversed(),
        open.stream().filter(s -> s.overall() == SlaStatus.AT_RISK).toList(),
        open.stream().filter(s -> s.overall() == SlaStatus.BREACHED).toList());
  }

  private Breakdown breakdown(String code, String name, IncidentQuery base) {
    return new Breakdown(
        code,
        name,
        incidents.count(base),
        incidents.count(base.withStatuses(IncidentQuery.OPEN)),
        incidents.count(base.withPriorities(Set.of(Priority.P1))));
  }

  private void requireReports(UserId viewerId) {
    users.get(viewerId).requirePermission(Permission.VIEW_REPORTS);
  }

  private static double round(double v) {
    return Math.round(v * 10.0) / 10.0;
  }
}
