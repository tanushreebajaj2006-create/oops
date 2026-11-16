/**
 * Domain exception hierarchy.
 *
 * <p>All business-rule failures extend the abstract {@link
 * com.servicedesk.domain.exception.DomainException}. Each subclass names exactly one kind of
 * failure from the project brief (invalid state transition, technician unavailable, workload
 * exceeded, service not found, missing SLA configuration, duplicate incident, unauthorized
 * operation, invalid input...). Infrastructure failures such as database or network errors, and
 * login failures, are handled in the outer layers, not here.
 */
package com.servicedesk.domain.exception;
