package com.servicedesk.api;

import com.servicedesk.api.dto.Views;
import com.servicedesk.api.dto.Views.IncidentDetail;
import com.servicedesk.api.dto.Views.IncidentSummary;
import com.servicedesk.api.dto.Views.SlaMeasureView;
import com.servicedesk.api.dto.Views.SlaView;
import com.servicedesk.api.dto.Views.UserRef;
import com.servicedesk.api.dto.Views.UserView;
import com.servicedesk.application.SlaService;
import com.servicedesk.domain.assignment.AssignmentCandidate;
import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.common.NamedStrategy;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.notification.InAppInbox.InboxItem;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaAlert;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaMeasure;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.sla.SlaSnapshot;
import com.servicedesk.domain.sla.SlaTarget;
import com.servicedesk.domain.user.Employee;
import com.servicedesk.domain.user.Skill;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Converts domain objects into API views. Stateless; all methods are static. */
public final class ApiMapper {

  private ApiMapper() {}

  public static UserRef ref(User u) {
    return u == null ? null : new UserRef(u.id().value(), u.fullName(), u.role());
  }

  public static UserView user(User u) {
    Map<String, Integer> skills = null;
    Integer experience = null;
    Integer max = null;
    Integer active = null;
    Boolean available = null;
    if (u instanceof Technician t) {
      skills = new LinkedHashMap<>();
      for (Skill s : t.skills()) {
        skills.put(s.categoryCode(), s.proficiency());
      }
      experience = t.experienceYears();
      max = t.maxWorkload();
      active = t.activeWorkload();
      available = t.isAvailable();
    }
    return new UserView(
        u.id().value(),
        u.role(),
        u.fullName(),
        u.email(),
        u.phone().orElse(null),
        u.isActive(),
        u instanceof Employee e ? e.department() : null,
        skills,
        experience,
        max,
        active,
        available);
  }

  public static IncidentSummary summary(Incident i, SlaCalculator calculator) {
    Optional<SlaSnapshot> sla = SlaService.snapshot(i, calculator);
    return new IncidentSummary(
        i.id().value(),
        i.title(),
        i.status(),
        i.priority().orElse(null),
        i.category().map(Category::code).orElse(null),
        i.service().code(),
        i.service().name(),
        ref(i.reporter()),
        i.assignee().map(ApiMapper::ref).orElse(null),
        i.createdAt(),
        i.updatedAt(),
        sla.map(SlaSnapshot::overall).orElse(null),
        sla.map(s -> s.resolution().dueAt()).orElse(null),
        i.escalationLevel());
  }

  public static IncidentDetail detail(Incident i, User viewer, Optional<SlaSnapshot> sla) {
    return new IncidentDetail(
        i.id().value(),
        i.title(),
        i.description(),
        i.status(),
        i.priority().orElse(null),
        i.category().map(Category::code).orElse(null),
        i.service().code(),
        i.service().name(),
        i.service().criticality(),
        i.impact(),
        i.urgency(),
        i.affectedUsers(),
        ref(i.reporter()),
        i.assignee().map(ApiMapper::ref).orElse(null),
        i.createdAt(),
        i.updatedAt(),
        i.acknowledgedAt().orElse(null),
        i.resolvedAt().orElse(null),
        i.reopenCount(),
        i.escalationLevel(),
        sla.map(ApiMapper::sla).orElse(null),
        i.notesVisibleTo(viewer).stream()
            .map(n -> new Views.NoteView(ref(n.author()), n.type(), n.text(), n.createdAt()))
            .toList(),
        i.resolution()
            .map(
                r ->
                    new Views.ResolutionView(
                        r.summary(), r.rootCause(), ref(r.resolvedBy()), r.resolvedAt()))
            .orElse(null),
        i.feedback()
            .map(f -> new Views.FeedbackView(f.rating(), f.comment(), f.givenAt()))
            .orElse(null),
        i.closure()
            .map(c -> new Views.ClosureView(ref(c.closedBy()), c.closedAt(), c.note()))
            .orElse(null),
        i.allowedActions());
  }

  public static SlaView sla(SlaSnapshot s) {
    return new SlaView(
        s.policyName(),
        s.priority(),
        s.overall(),
        measure(s.response(), s.evaluatedAt()),
        measure(s.resolution(), s.evaluatedAt()));
  }

  private static SlaMeasureView measure(SlaMeasure m, Instant now) {
    return new SlaMeasureView(
        m.dueAt(),
        m.completedAt(),
        m.status(),
        (int) Math.round(m.elapsedFraction() * 100),
        m.remaining(now).toMinutes());
  }

  public static Views.AuditEntryView audit(AuditEntry e) {
    return new Views.AuditEntryView(
        e.sequence(),
        e.timestamp(),
        e.actorId().value(),
        e.actorRole(),
        e.action(),
        e.incident().map(IncidentId::value).orElse(null),
        e.previousStatus(),
        e.newStatus(),
        e.description());
  }

  public static Views.NotificationView notification(InboxItem item) {
    var n = item.notification();
    return new Views.NotificationView(
        item.id(),
        n.subject(),
        n.body(),
        n.incidentId().value(),
        n.eventType(),
        n.urgent(),
        n.createdAt(),
        item.read());
  }

  public static Views.CandidateView candidate(AssignmentCandidate c) {
    Technician t = c.technician();
    return new Views.CandidateView(
        ref(t), c.score(), c.reason(), t.activeWorkload(), t.maxWorkload());
  }

  public static Views.ServiceView service(ItService s) {
    return new Views.ServiceView(s.code(), s.name(), s.criticality(), s.active());
  }

  public static Views.CategoryView category(Category c) {
    return new Views.CategoryView(c.code(), c.name(), c.active());
  }

  public static Views.SlaPolicyView slaPolicy(SlaPolicy p) {
    Map<Priority, Views.SlaTargetView> targets = new EnumMap<>(Priority.class);
    for (Map.Entry<Priority, SlaTarget> e : p.targets().entrySet()) {
      targets.put(
          e.getKey(),
          new Views.SlaTargetView(
              e.getValue().response().toMinutes(), e.getValue().resolution().toMinutes()));
    }
    return new Views.SlaPolicyView(
        p.name(), p.serviceCode().orElse(null), p.warningThreshold(), targets);
  }

  public static Views.StrategyView strategy(NamedStrategy s) {
    return new Views.StrategyView(s.name(), s.description());
  }

  public static Views.SlaAlertView alert(SlaAlert a) {
    return new Views.SlaAlertView(
        a.type().name(),
        a.incidentId().value(),
        a.kind().name(),
        a.priority(),
        a.dueAt(),
        a.detectedAt(),
        a.assigneeId().map(UserId::value).orElse(null),
        a.overdueBy().toMinutes());
  }

  public static Views.SlaSnapshotRow slaRow(SlaSnapshot s) {
    return new Views.SlaSnapshotRow(s.incidentId().value(), s.priority(), s.overall(), sla(s));
  }
}
