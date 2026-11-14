package com.servicedesk.domain.audit;

import com.servicedesk.domain.incident.IncidentId;
import java.util.List;

/**
 * Append-only store of {@link AuditEntry}s (<b>Repository</b> contract). There is deliberately no
 * update or delete: an audit trail that can be edited is not an audit trail.
 *
 * <p>Phase 4 provides the database implementation; {@link InMemoryAuditLog} serves tests and demos.
 */
public interface AuditLog {

  /**
   * Stores the entry and returns it with its assigned sequence number.
   *
   * @return the stored entry
   */
  AuditEntry append(AuditEntry entry);

  /** Entries matching the query, oldest first. */
  List<AuditEntry> search(AuditQuery query);

  /** Complete history of one incident, oldest first. */
  default List<AuditEntry> historyOf(IncidentId incidentId) {
    return search(AuditQuery.forIncident(incidentId));
  }
}
