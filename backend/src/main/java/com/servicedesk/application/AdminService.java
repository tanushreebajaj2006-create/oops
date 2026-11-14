package com.servicedesk.application;

import com.servicedesk.application.port.SettingsStore;
import com.servicedesk.domain.assignment.AssignmentStrategy;
import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.audit.AuditQuery;
import com.servicedesk.domain.common.NamedStrategy;
import com.servicedesk.domain.common.StrategyRegistry;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.CategoryRepository;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.incident.ItServiceRepository;
import com.servicedesk.domain.priority.PriorityStrategy;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.sla.SlaPolicyRepository;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * System configuration by administrators: IT services, categories, SLA policies, the active
 * assignment and priority strategies, and the audit log. Every change is itself audited.
 */
@Service
@Transactional
public class AdminService {

  private final UserRepository users;
  private final ItServiceRepository services;
  private final CategoryRepository categories;
  private final SlaPolicyRepository slaPolicies;
  private final StrategyRegistry<AssignmentStrategy> assignmentStrategies;
  private final StrategyRegistry<PriorityStrategy> priorityStrategies;
  private final SettingsStore settings;
  private final AuditLog auditLog;
  private final Clock clock;

  public AdminService(
      UserRepository users,
      ItServiceRepository services,
      CategoryRepository categories,
      SlaPolicyRepository slaPolicies,
      StrategyRegistry<AssignmentStrategy> assignmentStrategies,
      StrategyRegistry<PriorityStrategy> priorityStrategies,
      SettingsStore settings,
      AuditLog auditLog,
      Clock clock) {
    this.users = users;
    this.services = services;
    this.categories = categories;
    this.slaPolicies = slaPolicies;
    this.assignmentStrategies = assignmentStrategies;
    this.priorityStrategies = priorityStrategies;
    this.settings = settings;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  /** The active strategy of each kind and what else is available. */
  public record StrategySettings(
      NamedStrategy assignment,
      List<? extends NamedStrategy> assignmentOptions,
      NamedStrategy priority,
      List<? extends NamedStrategy> priorityOptions) {}

  // ---- reference data (readable by everyone, for forms) -------------------------------

  @Transactional(readOnly = true)
  public List<ItService> listServices() {
    return services.findAll();
  }

  @Transactional(readOnly = true)
  public List<Category> listCategories() {
    return categories.findAll();
  }

  public ItService saveService(UserId adminId, ItService service) {
    User admin = admin(adminId);
    boolean isNew = services.find(service.code()).isEmpty();
    services.save(service);
    audit(
        admin,
        isNew ? "SERVICE_CREATED" : "SERVICE_UPDATED",
        service.code()
            + " ("
            + service.name()
            + ", "
            + service.criticality()
            + (service.active() ? "" : ", retired")
            + ")");
    return service;
  }

  /** Adds or edits a category - no code change needed for a new category. */
  public Category saveCategory(UserId adminId, Category category) {
    User admin = admin(adminId);
    boolean isNew = categories.find(category.code()).isEmpty();
    categories.save(category);
    audit(
        admin,
        isNew ? "CATEGORY_CREATED" : "CATEGORY_UPDATED",
        category.code() + " (" + category.name() + (category.active() ? "" : ", retired") + ")");
    return category;
  }

  // ---- SLA policies -----------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<SlaPolicy> listSlaPolicies(UserId viewerId) {
    User viewer = users.get(viewerId);
    if (!viewer.can(Permission.CONFIGURE_SYSTEM) && !viewer.can(Permission.VIEW_REPORTS)) {
      throw new UnauthorizedOperationException(viewerId + " cannot view SLA policies");
    }
    return slaPolicies.findAll();
  }

  public SlaPolicy saveSlaPolicy(UserId adminId, SlaPolicy policy) {
    User admin = admin(adminId);
    policy.serviceCode().ifPresent(services::get); // the service must exist
    slaPolicies.save(policy);
    audit(
        admin,
        "SLA_POLICY_SAVED",
        policy.name()
            + " for "
            + policy.serviceCode().orElse("all services (default)")
            + ": "
            + policy.targets());
    return policy;
  }

  public void deleteServiceSlaPolicy(UserId adminId, String serviceCode) {
    User admin = admin(adminId);
    slaPolicies.deleteServicePolicy(serviceCode);
    audit(admin, "SLA_POLICY_DELETED", "Service policy removed for " + serviceCode.toUpperCase());
  }

  // ---- strategies ------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public StrategySettings strategies() {
    return new StrategySettings(
        assignmentStrategies.active(),
        List.copyOf(assignmentStrategies.available()),
        priorityStrategies.active(),
        List.copyOf(priorityStrategies.available()));
  }

  /**
   * Switches the active assignment and/or priority strategy at runtime ({@code null} = keep). The
   * choice is stored, so it survives a restart.
   */
  public StrategySettings changeStrategies(
      UserId adminId, String assignmentName, String priorityName) {
    User admin = admin(adminId);
    if (assignmentName != null) {
      String before = assignmentStrategies.active().name();
      String after = assignmentStrategies.activate(assignmentName).name();
      settings.put(SettingsStore.ASSIGNMENT_STRATEGY, after, admin.id(), clock.instant());
      audit(admin, "STRATEGY_CHANGED", "Assignment strategy " + before + " -> " + after);
    }
    if (priorityName != null) {
      String before = priorityStrategies.active().name();
      String after = priorityStrategies.activate(priorityName).name();
      settings.put(SettingsStore.PRIORITY_STRATEGY, after, admin.id(), clock.instant());
      audit(admin, "STRATEGY_CHANGED", "Priority strategy " + before + " -> " + after);
    }
    return strategies();
  }

  // ---- audit log ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<AuditEntry> searchAudit(UserId viewerId, AuditQuery query) {
    users.get(viewerId).requirePermission(Permission.VIEW_AUDIT_LOG);
    return auditLog.search(query);
  }

  private User admin(UserId id) {
    User user = users.get(id);
    user.requirePermission(Permission.CONFIGURE_SYSTEM);
    return user;
  }

  private void audit(User actor, String action, String description) {
    auditLog.append(AuditEntry.adminAction(actor, action, description, clock.instant()));
  }
}
