package com.servicedesk.api;

import com.servicedesk.api.dto.Requests;
import com.servicedesk.api.dto.Views;
import com.servicedesk.application.IncidentService;
import com.servicedesk.application.Page;
import com.servicedesk.application.SlaService;
import com.servicedesk.application.UserService;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.sla.SlaStatus;
import com.servicedesk.domain.user.UserId;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incidents: report, search, view, history, and one endpoint per lifecycle action. Which actions a
 * user may perform is decided by the domain (role + relationship + current state) and returned as
 * {@code allowedActions} so the UI only shows sensible buttons.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

  private final IncidentService incidents;
  private final UserService users;
  private final SlaService sla;

  public IncidentController(IncidentService incidents, UserService users, SlaService sla) {
    this.incidents = incidents;
    this.users = users;
    this.sla = sla;
  }

  @PostMapping
  public ResponseEntity<Views.IncidentDetail> report(
      @Valid @RequestBody Requests.ReportIncident body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    Incident created =
        incidents.report(
            me,
            new IncidentService.ReportCommand(
                body.title(),
                body.description(),
                body.serviceCode(),
                body.impact(),
                body.urgency(),
                body.affectedUsers() == null ? 1 : body.affectedUsers(),
                body.categoryCode()));
    return ResponseEntity.created(URI.create("/api/v1/incidents/" + created.id()))
        .body(detail(created, me));
  }

  /**
   * Search with any combination of filters: id, status, priority, category, service, technician,
   * reporter, created date range, free text and SLA status. Results are limited to what the caller
   * may see.
   */
  @GetMapping
  public Views.PageView<Views.IncidentSummary> search(
      @RequestParam(required = false) String id,
      @RequestParam(required = false) List<IncidentStatus> status,
      @RequestParam(required = false) List<Priority> priority,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String service,
      @RequestParam(required = false) String technician,
      @RequestParam(required = false) String reporter,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) SlaStatus sla,
      @RequestParam(defaultValue = "" + IncidentQuery.DEFAULT_LIMIT) int limit,
      @RequestParam(defaultValue = "0") int offset,
      Authentication auth) {
    IncidentQuery query =
        IncidentQuery.all()
            .withId(id == null || id.isBlank() ? null : new IncidentId(id))
            .withStatuses(status == null ? null : new HashSet<>(status))
            .withPriorities(priority == null ? null : new HashSet<>(priority))
            .withCategory(category)
            .withService(service)
            .assignedTo(blank(technician) ? null : new UserId(technician))
            .reportedBy(blank(reporter) ? null : new UserId(reporter))
            .createdBetween(from, to)
            .containing(q)
            .page(limit, offset);
    Page<Incident> page = incidents.search(CurrentUser.id(auth), query, sla);
    SlaCalculator calculator = this.sla.calculator();
    return new Views.PageView<>(
        page.items().stream().map(i -> ApiMapper.summary(i, calculator)).toList(),
        page.total(),
        page.limit(),
        page.offset());
  }

  @GetMapping("/{id}")
  public Views.IncidentDetail get(@PathVariable String id, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.get(me, new IncidentId(id)), me);
  }

  @GetMapping("/{id}/history")
  public List<Views.AuditEntryView> history(@PathVariable String id, Authentication auth) {
    return incidents.history(CurrentUser.id(auth), new IncidentId(id)).stream()
        .map(ApiMapper::audit)
        .toList();
  }

  @GetMapping("/{id}/recommendations")
  public List<Views.CandidateView> recommendations(@PathVariable String id, Authentication auth) {
    return incidents.recommend(CurrentUser.id(auth), new IncidentId(id)).stream()
        .map(ApiMapper::candidate)
        .toList();
  }

  @PatchMapping("/{id}")
  public Views.IncidentDetail update(
      @PathVariable String id,
      @Valid @RequestBody Requests.UpdateIncident body,
      Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(
        incidents.updateDetails(
            me,
            new IncidentId(id),
            new IncidentService.DetailsCommand(
                body.title(),
                body.description(),
                body.impact(),
                body.urgency(),
                body.affectedUsers())),
        me);
  }

  @PostMapping("/{id}/classify")
  public Views.IncidentDetail classify(
      @PathVariable String id, @Valid @RequestBody Requests.Classify body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.classify(me, new IncidentId(id), body.categoryCode()), me);
  }

  @PostMapping("/{id}/priority")
  public Views.IncidentDetail priority(
      @PathVariable String id, @Valid @RequestBody Requests.SetPriority body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.setPriority(me, new IncidentId(id), body.priority()), me);
  }

  @PostMapping("/{id}/assign")
  public Views.IncidentDetail assign(
      @PathVariable String id,
      @RequestBody(required = false) Requests.Assign body,
      Authentication auth) {
    UserId me = CurrentUser.id(auth);
    String tech = body == null ? null : body.technicianId();
    return detail(
        incidents.assign(me, new IncidentId(id), blank(tech) ? null : new UserId(tech)), me);
  }

  @PostMapping("/{id}/acknowledge")
  public Views.IncidentDetail acknowledge(@PathVariable String id, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.acknowledge(me, new IncidentId(id)), me);
  }

  @PostMapping("/{id}/start")
  public Views.IncidentDetail start(@PathVariable String id, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.startWork(me, new IncidentId(id)), me);
  }

  @PostMapping("/{id}/request-info")
  public Views.IncidentDetail requestInfo(
      @PathVariable String id, @Valid @RequestBody Requests.Text body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.requestInfo(me, new IncidentId(id), body.text()), me);
  }

  @PostMapping("/{id}/provide-info")
  public Views.IncidentDetail provideInfo(
      @PathVariable String id, @Valid @RequestBody Requests.Text body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.provideInfo(me, new IncidentId(id), body.text()), me);
  }

  @PostMapping("/{id}/notes")
  public Views.IncidentDetail addNote(
      @PathVariable String id, @Valid @RequestBody Requests.AddNote body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.addNote(me, new IncidentId(id), body.type(), body.text()), me);
  }

  @PostMapping("/{id}/resolve")
  public Views.IncidentDetail resolve(
      @PathVariable String id, @Valid @RequestBody Requests.Resolve body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.resolve(me, new IncidentId(id), body.summary(), body.rootCause()), me);
  }

  @PostMapping("/{id}/verify")
  public Views.IncidentDetail verify(@PathVariable String id, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.verify(me, new IncidentId(id)), me);
  }

  @PostMapping("/{id}/reopen")
  public Views.IncidentDetail reopen(
      @PathVariable String id, @Valid @RequestBody Requests.Text body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.reopen(me, new IncidentId(id), body.text()), me);
  }

  @PostMapping("/{id}/close")
  public Views.IncidentDetail close(
      @PathVariable String id,
      @Valid @RequestBody(required = false) Requests.Close body,
      Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.close(me, new IncidentId(id), body == null ? null : body.note()), me);
  }

  @PostMapping("/{id}/cancel")
  public Views.IncidentDetail cancel(
      @PathVariable String id, @Valid @RequestBody Requests.Text body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(incidents.cancel(me, new IncidentId(id), body.text()), me);
  }

  @PostMapping("/{id}/feedback")
  public Views.IncidentDetail feedback(
      @PathVariable String id, @Valid @RequestBody Requests.Feedback body, Authentication auth) {
    UserId me = CurrentUser.id(auth);
    return detail(
        incidents.giveFeedback(me, new IncidentId(id), body.rating(), body.comment()), me);
  }

  private Views.IncidentDetail detail(Incident incident, UserId viewer) {
    return ApiMapper.detail(incident, users.me(viewer), incidents.slaOf(incident));
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
