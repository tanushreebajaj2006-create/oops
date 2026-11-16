package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.audit.AuditQuery;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only {@link AuditLog} in the {@code audit_log} table. There is intentionally no update or
 * delete path anywhere in the code.
 */
@Repository
@Transactional
public class JpaAuditLog implements AuditLog {

  static final int DESCRIPTION_MAX = 4000;

  private final SpringDataAuditRepository jpa;

  public JpaAuditLog(SpringDataAuditRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public AuditEntry append(AuditEntry entry) {
    AuditEntryEntity saved =
        jpa.save(
            new AuditEntryEntity(
                entry.timestamp(),
                entry.actorId().value(),
                entry.actorRole().name(),
                entry.action(),
                entry.incident().map(IncidentId::value).orElse(null),
                entry.previousStatus() == null ? null : entry.previousStatus().name(),
                entry.newStatus() == null ? null : entry.newStatus().name(),
                truncate(entry.description())));
    return entry.withSequence(saved.seq);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AuditEntry> search(AuditQuery query) {
    return jpa.findAll(matching(query), Sort.by("seq")).stream()
        .map(JpaAuditLog::toDomain)
        .toList();
  }

  static Specification<AuditEntryEntity> matching(AuditQuery q) {
    return (root, cq, cb) -> {
      List<Predicate> p = new ArrayList<>();
      if (q.incidentId() != null) {
        p.add(cb.equal(root.get("incidentId"), q.incidentId().value()));
      }
      if (q.actorId() != null) {
        p.add(cb.equal(root.get("actorId"), q.actorId().value()));
      }
      if (q.action() != null) {
        p.add(cb.equal(root.get("action"), q.action().toUpperCase()));
      }
      if (q.from() != null) {
        p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), q.from()));
      }
      if (q.to() != null) {
        p.add(cb.lessThan(root.get("occurredAt"), q.to()));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
  }

  private static AuditEntry toDomain(AuditEntryEntity e) {
    return new AuditEntry(
        e.seq,
        e.occurredAt,
        new UserId(e.actorId),
        Role.valueOf(e.actorRole),
        e.action,
        e.incidentId == null ? null : new IncidentId(e.incidentId),
        e.previousStatus == null ? null : IncidentStatus.valueOf(e.previousStatus),
        e.newStatus == null ? null : IncidentStatus.valueOf(e.newStatus),
        e.description);
  }

  /** Very long notes can make a description exceed the column; keep the start, mark the cut. */
  static String truncate(String s) {
    return s.length() <= DESCRIPTION_MAX ? s : s.substring(0, DESCRIPTION_MAX - 3) + "...";
  }
}
