package com.servicedesk.domain.sla;

import com.servicedesk.domain.incident.IncidentId;
import java.util.List;
import java.util.Map;

/**
 * Outcome of one {@link SlaMonitor} pass.
 *
 * @param newAlerts warnings and violations raised for the first time in this pass
 * @param skipped incidents that could not be evaluated, with the reason (e.g. missing SLA
 *     configuration). One bad incident never stops the scan of the others.
 */
public record SlaScanResult(List<SlaAlert> newAlerts, Map<IncidentId, String> skipped) {

  public SlaScanResult {
    newAlerts = List.copyOf(newAlerts);
    skipped = Map.copyOf(skipped);
  }

  public List<SlaAlert> warnings() {
    return newAlerts.stream().filter(a -> a.type() == SlaAlert.Type.WARNING).toList();
  }

  public List<SlaAlert> violations() {
    return newAlerts.stream().filter(a -> a.type() == SlaAlert.Type.VIOLATION).toList();
  }
}
