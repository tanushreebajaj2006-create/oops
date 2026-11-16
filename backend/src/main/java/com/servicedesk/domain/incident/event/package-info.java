/**
 * Incident domain events and the <b>Observer pattern</b>.
 *
 * <p>{@link com.servicedesk.domain.incident.Incident} records an {@link
 * com.servicedesk.domain.incident.event.IncidentEvent} for every important change. After the change
 * is saved, the application layer passes the events to the {@link
 * com.servicedesk.domain.incident.event.IncidentEventPublisher}, which notifies every {@link
 * com.servicedesk.domain.incident.event.IncidentEventListener} - the audit trail and the
 * notification dispatcher being the two built-in ones.
 */
package com.servicedesk.domain.incident.event;
