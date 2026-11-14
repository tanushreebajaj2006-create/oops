/**
 * Priority domain package.
 *
 * <p>{@link com.servicedesk.domain.priority.Priority} (P1-P4), the shared {@link
 * com.servicedesk.domain.priority.Level} scale used for impact, urgency and business criticality,
 * and the interchangeable {@link com.servicedesk.domain.priority.PriorityStrategy} implementations
 * (<b>Strategy pattern</b>). Strategies only see {@link
 * com.servicedesk.domain.priority.PriorityFactors}, never the incident itself, so this package has
 * no dependency on the rest of the domain.
 */
package com.servicedesk.domain.priority;
