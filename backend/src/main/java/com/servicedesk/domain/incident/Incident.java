package com.servicedesk.domain.incident;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.InvalidIncidentException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.incident.state.IncidentState;
import com.servicedesk.domain.incident.state.IncidentStates;
import com.servicedesk.domain.priority.Level;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.priority.PriorityAssessment;
import com.servicedesk.domain.priority.PriorityFactors;
import com.servicedesk.domain.priority.PriorityStrategy;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The incident aggregate: the central object of the system.
 *
 * <p><b>Encapsulation</b>: there are no public setters. Every change goes through a business method
 * ({@link #assignTo}, {@link #resolve}, ...) that checks three things in a fixed order:
 *
 * <ol>
 *   <li><b>Who</b> is acting (role / relationship to the incident) - else {@link
 *       UnauthorizedOperationException};
 *   <li><b>When</b>: the current {@link IncidentState} must allow the action - else {@code
 *       InvalidStateTransitionException} (<b>State pattern</b>);
 *   <li><b>What</b>: the data supplied is valid - else {@code InvalidInputException}.
 * </ol>
 *
 * Only after all checks pass is anything modified, so a failed call never leaves the incident
 * half-changed.
 *
 * <p><b>Relationships</b>: association with its reporter, assigned {@link Technician} and {@link
 * ItService}; <b>composition</b> of {@link Note}s, {@link Resolution}, {@link Feedback} and {@link
 * Closure}, which have no meaning outside their incident.
 */
public class Incident {

  // ---- identity & report data (fixed at creation) ----------------------------------------
  private final IncidentId id;
  private final User reporter;
  private final Instant createdAt;
  private String title;
  private String description;
  private ItService service;
  private Level impact;
  private Level urgency;
  private int affectedUsers;

  // ---- classification & lifecycle --------------------------------------------------------
  private Category category;
  private Priority priority;
  private IncidentState state;
  private Technician assignee;
  private Instant acknowledgedAt;
  private Instant resolvedAt;
  private Instant updatedAt;
  private int reopenCount;

  // ---- owned parts (composition) ---------------------------------------------------------
  private final List<Note> notes = new ArrayList<>();
  private Resolution resolution;
  private Feedback feedback;
  private Closure closure;

  /** Package-private: use {@link IncidentFactory} to create incidents. */
  Incident(
      IncidentId id,
      User reporter,
      String title,
      String description,
      ItService service,
      Level impact,
      Level urgency,
      int affectedUsers,
      Instant createdAt) {
    this.id = Require.notNull(id, "Incident id");
    this.reporter = Require.notNull(reporter, "Reporter");
    this.title = title;
    this.description = description;
    this.service = service;
    this.impact = impact;
    this.urgency = urgency;
    this.affectedUsers = affectedUsers;
    this.createdAt = Require.notNull(createdAt, "Creation time");
    this.updatedAt = createdAt;
    this.state = IncidentStates.initial();
  }

  // =========================================================================================
  // Lifecycle actions
  // =========================================================================================

  /** Sets (or changes) the category. NEW -> CLASSIFIED, or stays CLASSIFIED. */
  public void classify(Category newCategory, User actor, Instant at) {
    actor.requirePermission(Permission.CLASSIFY_INCIDENTS);
    IncidentState next = state.classify();
    Require.notNull(newCategory, "Category");
    if (!newCategory.active()) {
      throw new InvalidIncidentException("Category " + newCategory.code() + " is retired");
    }
    this.category = newCategory;
    moveTo(next, at);
  }

  /**
   * Records the priority calculated by a priority strategy. Allowed while the incident is open
   * (priority can change, e.g. when more users are affected).
   */
  public void applyPriority(Priority newPriority, User actor, Instant at) {
    actor.requirePermission(Permission.CLASSIFY_INCIDENTS);
    requireOpen("change priority");
    this.priority = Require.notNull(newPriority, "Priority");
    this.updatedAt = at;
  }

  /**
   * Lets a {@link PriorityStrategy} compute this incident's priority and records the result.
   *
   * <p>The incident does not know <i>how</i> priority is calculated - it only supplies its {@link
   * #priorityFactors()} (<b>polymorphism</b>: any strategy can be passed in).
   *
   * @return the full assessment, including the rationale for the audit trail
   */
  public PriorityAssessment prioritize(PriorityStrategy strategy, User actor, Instant at) {
    Require.notNull(strategy, "Priority strategy");
    actor.requirePermission(Permission.CLASSIFY_INCIDENTS);
    requireOpen("change priority");
    PriorityAssessment assessment = strategy.assess(priorityFactors());
    applyPriority(assessment.priority(), actor, at);
    return assessment;
  }

  /** The inputs priority strategies are allowed to see. */
  public PriorityFactors priorityFactors() {
    return new PriorityFactors(impact, urgency, service.criticality(), affectedUsers);
  }

  /**
   * Gives the incident to a technician (first assignment, re-assignment or escalation).
   *
   * <p>The new technician's workload is checked first; only if they accept is the previous
   * technician released. Requires category and priority to be known.
   */
  public void assignTo(Technician technician, User actor, Instant at) {
    actor.requirePermission(Permission.ASSIGN_INCIDENTS);
    IncidentState next = state.assign();
    Require.notNull(technician, "Technician");
    if (category == null || priority == null) {
      throw new InvalidIncidentException(
          id + " must have a category and priority before assignment");
    }
    if (technician.equals(assignee)) {
      throw new InvalidIncidentException(id + " is already assigned to " + technician.id());
    }
    technician.takeOn(); // may throw TechnicianUnavailable / WorkloadExceeded
    if (assignee != null) {
      assignee.release();
    }
    this.assignee = technician;
    moveTo(next, at);
  }

  /** The assigned technician accepts the incident. The first time counts as the SLA response. */
  public void acknowledge(User actor, Instant at) {
    requireAssignee(actor);
    IncidentState next = state.acknowledge();
    if (acknowledgedAt == null) {
      acknowledgedAt = at;
    }
    moveTo(next, at);
  }

  public void startWork(User actor, Instant at) {
    requireAssignee(actor);
    moveTo(state.startWork(), at);
  }

  /** Technician asks the reporter a question; the incident waits for the answer. */
  public void requestInfo(User actor, String question, Instant at) {
    requireAssignee(actor);
    IncidentState next = state.requestInfo();
    Note note = new Note(actor, NoteType.INFO_REQUEST, question, at);
    notes.add(note);
    moveTo(next, at);
  }

  /** Reporter answers the technician's question; work resumes. */
  public void provideInfo(User actor, String answer, Instant at) {
    requireReporter(actor);
    IncidentState next = state.provideInfo();
    Note note = new Note(actor, NoteType.INFO_RESPONSE, answer, at);
    notes.add(note);
    moveTo(next, at);
  }

  /**
   * Adds an investigation note or a general comment. Information requests and responses have their
   * own actions because they change the state.
   */
  public void addNote(User actor, NoteType type, String text, Instant at) {
    Require.notNull(type, "Note type");
    if (type == NoteType.INFO_REQUEST || type == NoteType.INFO_RESPONSE) {
      throw new InvalidIncidentException("Use requestInfo / provideInfo for " + type);
    }
    if (type == NoteType.INVESTIGATION && !isAssignee(actor) && !isSupervisor(actor)) {
      throw new UnauthorizedOperationException(
          "Only the assigned technician can add investigation notes");
    }
    if (!isInvolved(actor)) {
      throw new UnauthorizedOperationException(actor.id() + " is not involved in " + id);
    }
    if (isTerminal()) {
      throw new InvalidIncidentException(id + " is " + status() + "; notes can no longer be added");
    }
    notes.add(new Note(actor, type, text, at));
    this.updatedAt = at;
  }

  /** Technician records the fix. Their workload drops by one. */
  public void resolve(User actor, String summary, String rootCause, Instant at) {
    requireAssignee(actor);
    IncidentState next = state.resolve();
    Resolution newResolution = new Resolution(summary, rootCause, actor, at);
    this.resolution = newResolution;
    this.resolvedAt = at;
    assignee.release();
    moveTo(next, at);
  }

  /** Reporter (or an administrator) confirms the fix works. */
  public void verify(User actor, Instant at) {
    requireReporterOrAdmin(actor);
    moveTo(state.verify(), at);
  }

  /** Reporter says the problem is not fixed. Work returns to the same technician. */
  public void reopen(User actor, String reason, Instant at) {
    requireReporterOrAdmin(actor);
    IncidentState next = state.reopen();
    Note note =
        new Note(actor, NoteType.COMMENT, "Reopened: " + Require.notBlank(reason, "Reason"), at);
    notes.add(note);
    this.resolution = null;
    this.resolvedAt = null;
    this.reopenCount++;
    assignee.resume();
    moveTo(next, at);
  }

  /** Final step. Allowed for the reporter, an administrator, or the system (auto-close). */
  public void close(User actor, String closingNote, Instant at) {
    if (!isReporter(actor) && !isAdmin(actor) && actor.role() != Role.SYSTEM) {
      throw new UnauthorizedOperationException(actor.id() + " cannot close " + id);
    }
    IncidentState next = state.close();
    this.closure = new Closure(actor, at, closingNote);
    moveTo(next, at);
  }

  /** Withdraws an incident before work starts (e.g. it was a duplicate or reported by mistake). */
  public void cancel(User actor, String reason, Instant at) {
    if (!isReporter(actor) && !actor.can(Permission.ASSIGN_INCIDENTS)) {
      throw new UnauthorizedOperationException(actor.id() + " cannot cancel " + id);
    }
    IncidentState next = state.cancel();
    String why = Require.notBlank(reason, "Cancellation reason");
    if (assignee != null) {
      assignee.release();
    }
    this.closure = new Closure(actor, at, why);
    moveTo(next, at);
  }

  /** Reporter rates the service once the incident is resolved. Only one rating is allowed. */
  public void giveFeedback(User actor, int rating, String comment, Instant at) {
    requireReporter(actor);
    IncidentStatus s = status();
    if (s != IncidentStatus.RESOLVED
        && s != IncidentStatus.VERIFIED
        && s != IncidentStatus.CLOSED) {
      throw new InvalidIncidentException("Feedback can only be given after resolution");
    }
    if (feedback != null) {
      throw new InvalidIncidentException("Feedback was already given for " + id);
    }
    this.feedback = new Feedback(rating, comment, at);
    this.updatedAt = at;
  }

  // =========================================================================================
  // Queries
  // =========================================================================================

  /** Whether {@code viewer} may see this incident at all. */
  public boolean isVisibleTo(User viewer) {
    return isInvolved(viewer);
  }

  /** Notes filtered for the viewer: reporters do not see internal investigation notes. */
  public List<Note> notesVisibleTo(User viewer) {
    if (isAssignee(viewer) || viewer.can(Permission.VIEW_ALL_INCIDENTS)) {
      return notes();
    }
    return notes.stream().filter(n -> n.type().visibleToReporter()).toList();
  }

  public Set<IncidentAction> allowedActions() {
    return state.allowedActions();
  }

  public IncidentStatus status() {
    return state.status();
  }

  public boolean isOpen() {
    return status().isOpen();
  }

  public boolean isTerminal() {
    return state.allowedActions().isEmpty();
  }

  public IncidentId id() {
    return id;
  }

  public User reporter() {
    return reporter;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  public ItService service() {
    return service;
  }

  public Level impact() {
    return impact;
  }

  public Level urgency() {
    return urgency;
  }

  public int affectedUsers() {
    return affectedUsers;
  }

  public Optional<Category> category() {
    return Optional.ofNullable(category);
  }

  public Optional<Priority> priority() {
    return Optional.ofNullable(priority);
  }

  public Optional<Technician> assignee() {
    return Optional.ofNullable(assignee);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> acknowledgedAt() {
    return Optional.ofNullable(acknowledgedAt);
  }

  public Optional<Instant> resolvedAt() {
    return Optional.ofNullable(resolvedAt);
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public int reopenCount() {
    return reopenCount;
  }

  /** Read-only view; the list can only grow through the business methods above. */
  public List<Note> notes() {
    return Collections.unmodifiableList(notes);
  }

  public Optional<Resolution> resolution() {
    return Optional.ofNullable(resolution);
  }

  public Optional<Feedback> feedback() {
    return Optional.ofNullable(feedback);
  }

  public Optional<Closure> closure() {
    return Optional.ofNullable(closure);
  }

  // =========================================================================================
  // Internal helpers
  // =========================================================================================

  private void moveTo(IncidentState next, Instant at) {
    this.state = next;
    this.updatedAt = Require.notNull(at, "Action time");
  }

  private void requireOpen(String what) {
    if (!isOpen()) {
      throw new InvalidIncidentException("Cannot " + what + " on a " + status() + " incident");
    }
  }

  private boolean isReporter(User user) {
    return reporter.equals(user);
  }

  private boolean isAssignee(User user) {
    return assignee != null && assignee.equals(user);
  }

  private boolean isAdmin(User user) {
    return user.role() == Role.ADMINISTRATOR && user.isActive();
  }

  private boolean isSupervisor(User user) {
    return user.can(Permission.VIEW_ALL_INCIDENTS) && user.role() != Role.SYSTEM;
  }

  private boolean isInvolved(User user) {
    return isReporter(user) || isAssignee(user) || user.can(Permission.VIEW_ALL_INCIDENTS);
  }

  private void requireAssignee(User actor) {
    if (!isAssignee(actor) || !actor.can(Permission.WORK_ON_INCIDENTS)) {
      throw new UnauthorizedOperationException(
          "Only the technician assigned to " + id + " can do this");
    }
  }

  private void requireReporter(User actor) {
    if (!isReporter(actor)) {
      throw new UnauthorizedOperationException("Only the reporter of " + id + " can do this");
    }
  }

  private void requireReporterOrAdmin(User actor) {
    if (!isReporter(actor) && !isAdmin(actor)) {
      throw new UnauthorizedOperationException(
          "Only the reporter of " + id + " or an administrator can do this");
    }
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof Incident other && id.equals(other.id));
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return id + " [" + status() + "] " + title;
  }
}
