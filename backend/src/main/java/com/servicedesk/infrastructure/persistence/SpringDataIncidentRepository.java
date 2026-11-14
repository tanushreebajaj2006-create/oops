package com.servicedesk.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code incidents}; used by the incident and user repositories. */
public interface SpringDataIncidentRepository
    extends JpaRepository<IncidentEntity, String>, JpaSpecificationExecutor<IncidentEntity> {

  long countByAssigneeIdAndStatusIn(String assigneeId, Collection<String> statuses);

  /** Rows of {@code [assigneeId, openCount]} for every technician with open work. */
  @Query(
      "select i.assigneeId, count(i) from IncidentEntity i "
          + "where i.assigneeId is not null and i.status in :statuses group by i.assigneeId")
  List<Object[]> countOpenByAssignee(@Param("statuses") Collection<String> statuses);
}
