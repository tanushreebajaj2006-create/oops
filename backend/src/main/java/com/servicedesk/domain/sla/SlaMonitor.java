package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.DomainException;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.UserId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Continuous SLA compliance check. A scheduled job (Phase 5) calls {@link #scan} every minute with
 * the open incidents; the monitor returns only <i>new</i> warnings and violations so that
 * notifications are sent once, not every minute.
 *
 * <p>Collaborators are injected through abstractions ({@link SlaCalculator}, {@link SlaAlertLog}),
 * which keeps the monitor free of any scheduling or database code.
 */
public class SlaMonitor {

  private final SlaCalculator calculator;
  private final SlaAlertLog log;

  public SlaMonitor(SlaCalculator calculator, SlaAlertLog log) {
    this.calculator = Require.notNull(calculator, "SLA calculator");
    this.log = Require.notNull(log, "SLA alert log");
  }

  public SlaScanResult scan(Collection<Incident> incidents) {
    Require.notNull(incidents, "Incidents");
    List<SlaAlert> fresh = new ArrayList<>();
    Map<IncidentId, String> skipped = new LinkedHashMap<>();

    for (Incident incident : incidents) {
      if (incident.priority().isEmpty()) {
        continue; // not yet prioritised: SLA starts counting but has no target to compare to yet
      }
      SlaSnapshot snapshot;
      try {
        snapshot = calculator.evaluate(incident);
      } catch (DomainException e) {
        skipped.put(incident.id(), e.getMessage());
        continue;
      }
      UserId assignee = incident.assignee().map(Technician::id).orElse(null);
      for (SlaKind kind : SlaKind.values()) {
        checkClock(snapshot, kind, assignee, fresh);
      }
    }
    return new SlaScanResult(fresh, skipped);
  }

  private void checkClock(SlaSnapshot snapshot, SlaKind kind, UserId assignee, List<SlaAlert> out) {
    SlaMeasure m = snapshot.measure(kind);
    SlaAlert.Type type =
        switch (m.status()) {
          case BREACHED -> SlaAlert.Type.VIOLATION;
          case AT_RISK -> SlaAlert.Type.WARNING;
          default -> null;
        };
    if (type == null || log.hasRaised(snapshot.incidentId(), kind, type)) {
      return;
    }
    // A clock that jumped straight to BREACHED (e.g. the job was down) gets no late warning.
    SlaAlert alert =
        new SlaAlert(
            type,
            snapshot.incidentId(),
            kind,
            snapshot.priority(),
            m.dueAt(),
            snapshot.evaluatedAt(),
            assignee);
    log.record(alert);
    out.add(alert);
  }
}
