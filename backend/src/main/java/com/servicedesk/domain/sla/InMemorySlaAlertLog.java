package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple {@link SlaAlertLog} kept in memory. A {@link HashSet} of keys gives O(1) "already raised?"
 * checks; an {@link ArrayList} keeps alerts in the order they happened.
 */
public class InMemorySlaAlertLog implements SlaAlertLog {

  private record Key(IncidentId incidentId, SlaKind kind, SlaAlert.Type type) {}

  private final Set<Key> raised = new HashSet<>();
  private final List<SlaAlert> alerts = new ArrayList<>();

  @Override
  public synchronized boolean hasRaised(IncidentId incidentId, SlaKind kind, SlaAlert.Type type) {
    return raised.contains(new Key(incidentId, kind, type));
  }

  @Override
  public synchronized void record(SlaAlert alert) {
    Require.notNull(alert, "SLA alert");
    if (raised.add(new Key(alert.incidentId(), alert.kind(), alert.type()))) {
      alerts.add(alert);
    }
  }

  @Override
  public synchronized List<SlaAlert> violations() {
    return alerts.stream().filter(a -> a.type() == SlaAlert.Type.VIOLATION).toList();
  }

  @Override
  public synchronized List<SlaAlert> forIncident(IncidentId incidentId) {
    return alerts.stream().filter(a -> a.incidentId().equals(incidentId)).toList();
  }

  public synchronized List<SlaAlert> all() {
    return Collections.unmodifiableList(new ArrayList<>(alerts));
  }
}
