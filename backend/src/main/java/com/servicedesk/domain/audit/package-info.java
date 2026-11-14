/**
 * Audit trail domain package.
 *
 * <p>{@link com.servicedesk.domain.audit.AuditTrailListener} observes every incident event and
 * appends an {@link com.servicedesk.domain.audit.AuditEntry} (timestamp, user, action, incident,
 * previous state, new state, description) to the append-only {@link
 * com.servicedesk.domain.audit.AuditLog}, which can be searched with an {@link
 * com.servicedesk.domain.audit.AuditQuery}.
 */
package com.servicedesk.domain.audit;
