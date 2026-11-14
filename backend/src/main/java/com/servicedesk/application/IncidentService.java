package com.servicedesk.application;

import com.servicedesk.domain.assignment.AssignmentCandidate;
import com.servicedesk.domain.assignment.TechnicianAssigner;
import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.common.StrategyRegistry;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentFactory;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentRepository;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.incident.NewIncident;
import com.servicedesk.domain.incident.NoteType;
import com.servicedesk.domain.incident.event.IncidentEventPublisher;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.priority.PriorityStrategy;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaSnapshot;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases for incidents: report, view, search and every lifecycle action.
 *
 * <p>Each command follows the same shape: load the acting user and the incident, ask the
 * <i>domain</i> to perform the action (it checks who / when / what), save, then publish the events
 * the incident recorded to the audit trail and notification observers. The service itself holds no
 * business rules beyond <i>orchestration</i> - which steps happen in which order - and read
 * visibility.
 *
 * <p>Reporting with a category runs the brief's automated scenario in one transaction: create ->
 * classify -> prioritise (active priority strategy) -> auto-assign (active assignment strategy) ->
 * notify -> audit.
 */
@Service
@Transactional
public class IncidentService {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentService.class);
  private static final SystemActor SYSTEM = SystemActor.INSTANCE;

  private final IncidentRepository incidents;
  private final UserRepository users;
  private final ItServiceRepository services;
  private final CategoryRepository categories;
  private final IncidentFactory factory;
  private final StrategyRegistry<PriorityStrategy> priorityStrategies;
  private final TechnicianAssigner assigner;
  private final IncidentEventPublisher publisher;
  private final AuditLog auditLog;
  private final SlaService sla;
  private final Clock clock;

  public IncidentService(
      IncidentRepository incidents,
      UserRepository users,
      ItServiceRepository services,
      CategoryRepository categories,
      IncidentFactory factory,
      StrategyRegistry<PriorityStrategy> priorityStrategies,
      TechnicianAssigner assigner,
      IncidentEventPublisher publisher,
      AuditLog auditLog,
      SlaService sla,
      Clock clock) {
    this.incidents = incidents;
    this.users = users;
    this.services = services;
    this.categories = categories;
    this.factory = factory;
    this.priorityStrategies = priorityStrategies;
    this.assigner = assigner;
    this.publisher = publisher;
    this.auditLog = auditLog;
    this.sla = sla;
    this.clock = clock;
  }

  /** What a user fills in to report a problem. The category is optional. */
  public record ReportCommand(
      String title,
      String description,
      String serviceCode,
      Level impact,
      Level urgency,
      int affectedUsers,
      String categoryCode) {}

  /** Corrected details of an open incident. */
  public record DetailsCommand(
      String title, String description, Level impact, Level urgency, int affectedUsers) {}

  // =======================================================================================
  // Report & read
  // =======================================================================================

  public Incident report(UserId reporterId, ReportCommand c) {
    User reporter = activeUser(reporterId);
    Incident incident =
        factory.create(
            new NewIncident(
                reporter,
                c.title(),
                c.description(),
                services.get(c.serviceCode()),
                c.impact(),
                c.urgency(),
                c.affectedUsers()));
    if (c.categoryCode() != null && !c.categoryCode().isBlank()) {
      // the reporter's choice is applied by the system, which then prioritises and assigns
      incident.classify(categories.get(c.categoryCode()), SYSTEM, clock.instant());
      prioritiseAndAssign(incident);
    }
    return persist(incident);
  }

  @Transactional(readOnly = true)
  public Incident get(UserId viewerId, IncidentId id) {
    User viewer = activeUser(viewerId);
    Incident incident = incidents.get(id);
    if (!incident.isVisibleTo(viewer)) {
      throw new UnauthorizedOperationException(viewer.id() + " cannot view " + id);
    }
    return incident;
  }

  /**
   * Searches with the requested filters, narrowed to what the viewer may see: supervisors see
   * everything, technicians the incidents assigned to them (or reported by them, if they ask for
   * that), employees only their own. The optional SLA status filter is applied on top, because SLA
   * status depends on the current time.
   */
  @Transactional(readOnly = true)
  public Page<Incident> search(UserId viewerId, IncidentQuery query, SlaStatus slaStatus) {
    User viewer = activeUser(viewerId);
    IncidentQuery scoped = scopeFor(viewer, query);
    if (slaStatus == null) {
      return new Page<>(
          incidents.search(scoped), incidents.count(scoped), query.limit(), query.offset());
    }
    SlaCalculator calculator = sla.calculator();
    List<Incident> matching =
        incidents.search(scoped.page(IncidentQuery.MAX_LIMIT, 0)).stream()
            .filter(
                i ->
                    SlaService.snapshot(i, calculator)
                        .map(s -> s.overall() == slaStatus)
                        .orElse(false))
            .toList();
    List<Incident> pageItems = matching.stream().skip(query.offset()).limit(query.limit()).toList();
    return new Page<>(pageItems, matching.size(), query.limit(), query.offset());
  }

  @Transactional(readOnly = true)
  public Optional<SlaSnapshot> slaOf(Incident incident) {
    return sla.snapshot(incident);
  }

  /** Complete audit history of one incident (anyone who can see the incident). */
  @Transactional(readOnly = true)
  public List<AuditEntry> history(UserId viewerId, IncidentId id) {
    get(viewerId, id);
    return auditLog.historyOf(id);
  }

  /** Ranked technicians for this incident according to the active strategy. */
  @Transactional(readOnly = true)
  public List<AssignmentCandidate> recommend(UserId actorId, IncidentId id) {
    User actor = activeUser(actorId);
    actor.requirePermission(Permission.ASSIGN_INCIDENTS);
    return assigner.recommend(incidents.get(id), users.findTechnicians());
  }

  // =======================================================================================
  // Lifecycle commands
  // =======================================================================================

  public Incident updateDetails(UserId actorId, IncidentId id, DetailsCommand c) {
    return change(
        actorId,
        id,
        (i, actor) -> {
          boolean factorsChanged =
              i.updateDetails(
                  actor,
                  c.title(),
                  c.description(),
                  c.impact(),
                  c.urgency(),
                  c.affectedUsers(),
                  now());
          if (factorsChanged && i.priority().isPresent()) {
            i.prioritize(priorityStrategies.active(), SYSTEM, now());
          }
        });
  }

  /** Sets the category; then prioritises and auto-assigns if that has not happened yet. */
  public Incident classify(UserId actorId, IncidentId id, String categoryCode) {
    return change(
        actorId,
        id,
        (i, actor) -> {
          i.classify(categories.get(categoryCode), actor, now());
          prioritiseAndAssign(i);
        });
  }

  /** Manual priority override by a technician, team lead or administrator. */
  public Incident setPriority(UserId actorId, IncidentId id, Priority priority) {
    if (priority == null) {
      throw new InvalidInputException("Priority is required");
    }
    return change(actorId, id, (i, actor) -> i.applyPriority(priority, actor, now()));
  }

  /**
   * Assigns to the named technician, or - if {@code technicianId} is null - to whoever the active
   * assignment strategy picks.
   */
  public Incident assign(UserId actorId, IncidentId id, UserId technicianId) {
    return change(
        actorId,
        id,
        (i, actor) -> {
          if (technicianId == null) {
            assigner.assign(i, users.findTechnicians(), actor, now());
            return;
          }
          if (!(users.get(technicianId) instanceof Technician tech)) {
            throw new InvalidInputException(technicianId + " is not a technician");
          }
          i.assignTo(tech, actor, now(), "Manual assignment by " + actor.id());
        });
  }

  public Incident acknowledge(UserId actorId, IncidentId id) {
    return change(actorId, id, (i, actor) -> i.acknowledge(actor, now()));
  }

  public Incident startWork(UserId actorId, IncidentId id) {
    return change(actorId, id, (i, actor) -> i.startWork(actor, now()));
  }

  public Incident requestInfo(UserId actorId, IncidentId id, String question) {
    return change(actorId, id, (i, actor) -> i.requestInfo(actor, question, now()));
  }

  public Incident provideInfo(UserId actorId, IncidentId id, String answer) {
    return change(actorId, id, (i, actor) -> i.provideInfo(actor, answer, now()));
  }

  public Incident addNote(UserId actorId, IncidentId id, NoteType type, String text) {
    return change(actorId, id, (i, actor) -> i.addNote(actor, type, text, now()));
  }

  public Incident resolve(UserId actorId, IncidentId id, String summary, String rootCause) {
    return change(actorId, id, (i, actor) -> i.resolve(actor, summary, rootCause, now()));
  }

  public Incident verify(UserId actorId, IncidentId id) {
    return change(actorId, id, (i, actor) -> i.verify(actor, now()));
  }

  public Incident reopen(UserId actorId, IncidentId id, String reason) {
    return change(actorId, id, (i, actor) -> i.reopen(actor, reason, now()));
  }

  public Incident close(UserId actorId, IncidentId id, String note) {
    return change(actorId, id, (i, actor) -> i.close(actor, note, now()));
  }

  public Incident cancel(UserId actorId, IncidentId id, String reason) {
    return change(actorId, id, (i, actor) -> i.cancel(actor, reason, now()));
  }

  public Incident giveFeedback(UserId actorId, IncidentId id, int rating, String comment) {
    return change(actorId, id, (i, actor) -> i.giveFeedback(actor, rating, comment, now()));
  }

  // =======================================================================================
  // Helpers
  // =======================================================================================

  private Incident change(UserId actorId, IncidentId id, BiConsumer<Incident, User> action) {
    User actor = activeUser(actorId);
    Incident incident = incidents.get(id);
    action.accept(incident, actor);
    return persist(incident);
  }

  /** Saves, then tells the observers (audit, notifications) what happened. */
  private Incident persist(Incident incident) {
    incidents.save(incident);
    publisher.publishAll(incident.pullEvents());
    return incident;
  }

  /** Steps 5-8 of the brief's scenario: calculate priority, then pick and assign a technician. */
  private void prioritiseAndAssign(Incident incident) {
    if (incident.priority().isEmpty()) {
      incident.prioritize(priorityStrategies.active(), SYSTEM, now());
    }
    if (incident.status() == IncidentStatus.CLASSIFIED && incident.assignee().isEmpty()) {
      try {
        assigner.assign(incident, users.findTechnicians(), SYSTEM, now());
      } catch (TechnicianUnavailableException e) {
        // nobody suitable is free: the incident waits in CLASSIFIED for a team lead to assign
        LOG.info("Auto-assignment of {} postponed: {}", incident.id(), e.getMessage());
      }
    }
  }

  private IncidentQuery scopeFor(User viewer, IncidentQuery q) {
    if (viewer.can(Permission.VIEW_ALL_INCIDENTS)) {
      return q;
    }
    if (viewer.can(Permission.WORK_ON_INCIDENTS) && !viewer.id().equals(q.reporterId())) {
      return q.assignedTo(viewer.id());
    }
    return q.reportedBy(viewer.id());
  }

  User activeUser(UserId id) {
    User user = users.get(id);
    if (!user.isActive()) {
      throw new UnauthorizedOperationException("Account " + id + " is deactivated");
    }
    return user;
  }

  private Instant now() {
    return clock.instant();
  }
}
