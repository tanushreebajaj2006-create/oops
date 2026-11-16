/**
 * Technician assignment domain package.
 *
 * <p>{@link com.servicedesk.domain.assignment.AssignmentStrategy} (<b>Strategy pattern</b>) with
 * four implementations - skill-based, load-based, round-robin and priority-based - and the {@link
 * com.servicedesk.domain.assignment.TechnicianAssigner} domain service that filters eligible
 * technicians and applies the strategy the administrator has made active.
 */
package com.servicedesk.domain.assignment;
