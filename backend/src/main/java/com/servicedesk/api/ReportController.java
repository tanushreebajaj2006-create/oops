package com.servicedesk.api;

import com.servicedesk.api.dto.Views;
import com.servicedesk.application.ReportService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboards and reports (team leads and administrators). */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('VIEW_REPORTS')")
public class ReportController {

  private final ReportService reports;

  public ReportController(ReportService reports) {
    this.reports = reports;
  }

  @GetMapping("/overview")
  public ReportService.Overview overview(Authentication auth) {
    return reports.overview(CurrentUser.id(auth));
  }

  @GetMapping("/technicians")
  public List<Views.TechnicianLoadView> technicians(Authentication auth) {
    return reports.technicianWorkload(CurrentUser.id(auth)).stream()
        .map(
            l ->
                new Views.TechnicianLoadView(
                    ApiMapper.ref(l.technician()),
                    l.activeIncidents(),
                    l.maxWorkload(),
                    (int) Math.round(100.0 * l.activeIncidents() / l.maxWorkload()),
                    l.technician().isAvailable(),
                    l.resolvedTotal()))
        .toList();
  }

  @GetMapping("/services")
  public List<ReportService.Breakdown> services(Authentication auth) {
    return reports.byService(CurrentUser.id(auth));
  }

  @GetMapping("/categories")
  public List<ReportService.Breakdown> categories(Authentication auth) {
    return reports.byCategory(CurrentUser.id(auth));
  }

  @GetMapping("/sla")
  public Views.SlaReportView sla(Authentication auth) {
    ReportService.SlaReport r = reports.slaReport(CurrentUser.id(auth));
    return new Views.SlaReportView(
        r.violations().stream().map(ApiMapper::alert).toList(),
        r.atRisk().stream().map(ApiMapper::slaRow).toList(),
        r.breachedOpen().stream().map(ApiMapper::slaRow).toList());
  }
}
