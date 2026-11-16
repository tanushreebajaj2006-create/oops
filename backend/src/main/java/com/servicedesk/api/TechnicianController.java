package com.servicedesk.api;

import com.servicedesk.api.dto.Requests;
import com.servicedesk.api.dto.Views;
import com.servicedesk.application.IncidentService;
import com.servicedesk.application.SlaService;
import com.servicedesk.application.UserService;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.incident.IncidentQuery;
import com.servicedesk.domain.sla.SlaCalculator;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Technician list for supervisors, and a technician's own queue and availability. */
@RestController
@RequestMapping("/api/v1/technicians")
public class TechnicianController {

  private final UserService users;
  private final IncidentService incidents;
  private final SlaService sla;

  public TechnicianController(UserService users, IncidentService incidents, SlaService sla) {
    this.users = users;
    this.incidents = incidents;
    this.sla = sla;
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('ASSIGN_INCIDENTS', 'VIEW_REPORTS')")
  public List<Views.UserView> list(Authentication auth) {
    return users.technicians(CurrentUser.id(auth)).stream().map(ApiMapper::user).toList();
  }

  /** The signed-in technician's open incidents and current workload. */
  @GetMapping("/me/workload")
  @PreAuthorize("hasAuthority('WORK_ON_INCIDENTS')")
  public Views.MyWorkload myWorkload(Authentication auth) {
    UserId me = CurrentUser.id(auth);
    User user = users.me(me);
    if (!(user instanceof Technician t)) {
      throw new UnauthorizedOperationException("Only technicians have a workload");
    }
    SlaCalculator calculator = sla.calculator();
    var open =
        incidents
            .search(me, IncidentQuery.all().assignedTo(me).withStatuses(IncidentQuery.OPEN), null)
            .items();
    return new Views.MyWorkload(
        ApiMapper.ref(t),
        t.activeWorkload(),
        t.maxWorkload(),
        t.isAvailable(),
        open.stream().map(i -> ApiMapper.summary(i, calculator)).toList());
  }

  @PutMapping("/me/availability")
  @PreAuthorize("hasAuthority('WORK_ON_INCIDENTS')")
  public Views.UserView setAvailability(
      @Valid @RequestBody Requests.Availability body, Authentication auth) {
    return ApiMapper.user(users.setOwnAvailability(CurrentUser.id(auth), body.available()));
  }
}
