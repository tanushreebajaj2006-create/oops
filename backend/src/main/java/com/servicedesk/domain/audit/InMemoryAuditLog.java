package com.servicedesk.domain.audit;

import com.servicedesk.domain.common.Require;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link AuditLog} kept in an {@link ArrayList}. Entries are only ever appended, so the list is
 * already in chronological order and searching is a single filtered pass.
 */
public class InMemoryAuditLog implements AuditLog {

  private final List<AuditEntry> entries = new ArrayList<>();
  private final AtomicLong sequence = new AtomicLong();

  @Override
  public synchronized AuditEntry append(AuditEntry entry) {
    Require.notNull(entry, "Audit entry");
    AuditEntry stored = entry.withSequence(sequence.incrementAndGet());
    entries.add(stored);
    return stored;
  }

  @Override
  public synchronized List<AuditEntry> search(AuditQuery query) {
    Require.notNull(query, "Audit query");
    return entries.stream().filter(query::matches).toList();
  }

  public synchronized int size() {
    return entries.size();
  }
}
