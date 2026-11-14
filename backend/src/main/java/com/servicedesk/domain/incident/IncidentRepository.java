package com.servicedesk.domain.incident;

import com.servicedesk.domain.exception.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * <b>Repository pattern</b>: the domain's view of incident storage - a collection it can add to and
 * look things up in, with no idea whether a database, a file or a map is behind it.
 *
 * <p>Also the {@link IncidentIdGenerator}, because ids come from a database sequence.
 */
public interface IncidentRepository extends IncidentIdGenerator {

  /** Inserts or updates the incident and returns it. */
  Incident save(Incident incident);

  Optional<Incident> find(IncidentId id);

  /**
   * @throws EntityNotFoundException if there is no such incident
   */
  default Incident get(IncidentId id) {
    return find(id).orElseThrow(() -> new EntityNotFoundException("Incident " + id + " not found"));
  }

  /** Matching incidents, newest first. */
  List<Incident> search(IncidentQuery query);

  /** Number of incidents matching the query (ignores limit/offset). */
  long count(IncidentQuery query);

  /** Every incident someone still has to work on - what the SLA monitor scans. */
  default List<Incident> findOpen() {
    return search(
        IncidentQuery.all().withStatuses(IncidentQuery.OPEN).page(IncidentQuery.MAX_LIMIT, 0));
  }
}
