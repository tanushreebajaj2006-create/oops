package com.servicedesk.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code sla_alerts}. */
public interface SpringDataSlaAlertRepository extends JpaRepository<SlaAlertEntity, Long> {

  boolean existsByIncidentIdAndSlaKindAndAlertType(
      String incidentId, String slaKind, String alertType);

  List<SlaAlertEntity> findByAlertTypeOrderByDetectedAtAscIdAsc(String alertType);

  List<SlaAlertEntity> findByIncidentIdOrderByDetectedAtAscIdAsc(String incidentId);
}
