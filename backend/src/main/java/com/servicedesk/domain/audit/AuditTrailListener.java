package com.servicedesk.domain.audit;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.event.IncidentEvent;
import com.servicedesk.domain.incident.event.IncidentEventListener;

/**
 * Observer that writes <b>every</b> incident event to the {@link AuditLog}, giving the complete
 * incident history the brief requires. Because it listens to events rather than being called from
 * each business method, no action can be forgotten: if the incident records it, it is audited.
 */
public class AuditTrailListener implements IncidentEventListener {

  private final AuditLog log;

  public AuditTrailListener(AuditLog log) {
    this.log = Require.notNull(log, "Audit log");
  }

  @Override
  public void onEvent(IncidentEvent event) {
    log.append(AuditEntry.from(event));
  }
}
