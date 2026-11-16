package com.servicedesk.api;

import com.servicedesk.api.dto.Requests;
import com.servicedesk.api.dto.Views;
import com.servicedesk.application.AdminService;
import com.servicedesk.application.SlaMonitoringService;
import com.servicedesk.application.UserService;
import com.servicedesk.domain.audit.AuditQuery;
import com.servicedesk.domain.incident.Category;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.ItService;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaPolicy;
import com.servicedesk.domain.sla.SlaTarget;
import com.servicedesk.domain.user.UserId;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator / IT manager functions: users and technicians, IT services, categories, SLA
 * policies, assignment and priority strategies, the audit log, and a manual SLA check.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AdminController {

  private final UserService users;
  private final AdminService admin;
  private final SlaMonitoringService slaMonitoring;

  public AdminController(
      UserService users, AdminService admin, SlaMonitoringService slaMonitoring) {
    this.users = users;
    this.admin = admin;
    this.slaMonitoring = slaMonitoring;
  }

  // ---- users -----------------------------------------------------------------------------

  @GetMapping("/users")
  public List<Views.UserView> users(Authentication auth) {
    return users.list(CurrentUser.id(auth)).stream().map(ApiMapper::user).toList();
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public Views.UserView createUser(@Valid @RequestBody Requests.CreateUser b, Authentication auth) {
    return ApiMapper.user(
        users.create(
            CurrentUser.id(auth),
            new UserService.CreateUserCommand(
                b.role(),
                b.fullName(),
                b.email(),
                b.password(),
                b.department(),
                b.phone(),
                b.skills(),
                b.experienceYears() == null ? 0 : b.experienceYears(),
                b.maxWorkload())));
  }

  @PatchMapping("/users/{id}")
  public Views.UserView updateUser(
      @PathVariable String id, @Valid @RequestBody Requests.UpdateUser b, Authentication auth) {
    return ApiMapper.user(
        users.update(
            CurrentUser.id(auth),
            new UserId(id),
            new UserService.UpdateUserCommand(
                b.fullName(),
                b.phone(),
                b.active(),
                b.department(),
                b.skills(),
                b.experienceYears(),
                b.maxWorkload(),
                b.available())));
  }

  // ---- services & categories ----------------------------------------------------------

  @PutMapping("/services/{code}")
  public Views.ServiceView saveService(
      @PathVariable String code, @Valid @RequestBody Requests.SaveService b, Authentication auth) {
    ItService service =
        new ItService(code, b.name(), b.criticality(), b.active() == null || b.active());
    return ApiMapper.service(admin.saveService(CurrentUser.id(auth), service));
  }

  @PutMapping("/categories/{code}")
  public Views.CategoryView saveCategory(
      @PathVariable String code, @Valid @RequestBody Requests.SaveCategory b, Authentication auth) {
    Category category = new Category(code, b.name(), b.active() == null || b.active());
    return ApiMapper.category(admin.saveCategory(CurrentUser.id(auth), category));
  }

  // ---- SLA policies -------------------------------------------------------------------------

  @GetMapping("/sla-policies")
  public List<Views.SlaPolicyView> slaPolicies(Authentication auth) {
    return admin.listSlaPolicies(CurrentUser.id(auth)).stream().map(ApiMapper::slaPolicy).toList();
  }

  /** Creates or replaces the default policy (no serviceCode) or a service's policy. */
  @PutMapping("/sla-policies")
  public Views.SlaPolicyView saveSlaPolicy(
      @Valid @RequestBody Requests.SaveSlaPolicy b, Authentication auth) {
    SlaPolicy policy =
        b.serviceCode() == null || b.serviceCode().isBlank()
            ? SlaPolicy.defaultPolicy(b.name())
            : SlaPolicy.forService(b.name(), b.serviceCode());
    if (b.warningThreshold() != null) {
      policy.setWarningThreshold(b.warningThreshold());
    }
    // apply in P1..P4 order so the consistency rule is checked against higher priorities first
    Map<Priority, Requests.SlaTargetMinutes> ordered = new TreeMap<>(b.targets());
    ordered.forEach(
        (p, t) ->
            policy.setTarget(
                p,
                SlaTarget.of(
                    Duration.ofMinutes(t.responseMinutes()),
                    Duration.ofMinutes(t.resolutionMinutes()))));
    return ApiMapper.slaPolicy(admin.saveSlaPolicy(CurrentUser.id(auth), policy));
  }

  @DeleteMapping("/sla-policies/{serviceCode}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteSlaPolicy(@PathVariable String serviceCode, Authentication auth) {
    admin.deleteServiceSlaPolicy(CurrentUser.id(auth), serviceCode);
  }

  /** Runs the SLA check immediately instead of waiting for the next scheduled run (demo aid). */
  @PostMapping("/sla-check")
  public SlaMonitoringService.RunResult runSlaCheck() {
    return slaMonitoring.runCheck();
  }

  // ---- strategies ---------------------------------------------------------------------------

  @GetMapping("/strategies")
  public Views.StrategiesView strategies() {
    return strategies(admin.strategies());
  }

  @PutMapping("/strategies")
  public Views.StrategiesView changeStrategies(
      @RequestBody Requests.ChangeStrategies b, Authentication auth) {
    return strategies(admin.changeStrategies(CurrentUser.id(auth), b.assignment(), b.priority()));
  }

  private static Views.StrategiesView strategies(AdminService.StrategySettings s) {
    return new Views.StrategiesView(
        ApiMapper.strategy(s.assignment()),
        s.assignmentOptions().stream().map(ApiMapper::strategy).toList(),
        ApiMapper.strategy(s.priority()),
        s.priorityOptions().stream().map(ApiMapper::strategy).toList());
  }

  // ---- audit log ----------------------------------------------------------------------------

  @GetMapping("/audit")
  public List<Views.AuditEntryView> audit(
      @RequestParam(required = false) String incident,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      Authentication auth) {
    AuditQuery q =
        new AuditQuery(
            blank(incident) ? null : new IncidentId(incident),
            blank(actor) ? null : new UserId(actor),
            blank(action) ? null : action,
            from,
            to);
    return admin.searchAudit(CurrentUser.id(auth), q).stream().map(ApiMapper::audit).toList();
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
