package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.sla.SlaAlert;
import com.servicedesk.domain.sla.SlaAlertLog;
import com.servicedesk.domain.sla.SlaKind;
import com.servicedesk.domain.user.UserId;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link SlaAlertLog} in the {@code sla_alerts} table. The unique constraint on (incident, kind,
 * type) is the final guarantee that an alert is recorded once even if two monitor runs overlap.
 */
@Repository
@Transactional
public class JpaSlaAlertLog implements SlaAlertLog {

  private final SpringDataSlaAlertRepository jpa;

  public JpaSlaAlertLog(SpringDataSlaAlertRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasRaised(IncidentId incidentId, SlaKind kind, SlaAlert.Type type) {
    return jpa.existsByIncidentIdAndSlaKindAndAlertType(
        incidentId.value(), kind.name(), type.name());
  }

  @Override
  public void record(SlaAlert a) {
    if (hasRaised(a.incidentId(), a.kind(), a.type())) {
      return;
    }
    jpa.save(
        new SlaAlertEntity(
            a.type().name(),
            a.incidentId().value(),
            a.kind().name(),
            a.priority().name(),
            a.dueAt(),
            a.detectedAt(),
            a.assigneeId().map(UserId::value).orElse(null)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<SlaAlert> violations() {
    return jpa.findByAlertTypeOrderByDetectedAtAscIdAsc(SlaAlert.Type.VIOLATION.name()).stream()
        .map(JpaSlaAlertLog::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<SlaAlert> forIncident(IncidentId incidentId) {
    return jpa.findByIncidentIdOrderByDetectedAtAscIdAsc(incidentId.value()).stream()
        .map(JpaSlaAlertLog::toDomain)
        .toList();
  }

  private static SlaAlert toDomain(SlaAlertEntity e) {
    return new SlaAlert(
        SlaAlert.Type.valueOf(e.alertType),
        new IncidentId(e.incidentId),
        SlaKind.valueOf(e.slaKind),
        Priority.valueOf(e.priority),
        e.dueAt,
        e.detectedAt,
        e.assigneeId == null ? null : new UserId(e.assigneeId));
  }
}
