package com.servicedesk.domain.priority;

import com.servicedesk.domain.common.NamedStrategy;

/**
 * <b>Strategy pattern</b>: one interchangeable algorithm for turning {@link PriorityFactors} into a
 * {@link Priority}.
 *
 * <p>Implementations:
 *
 * <ul>
 *   <li>{@link MatrixPriorityStrategy} - the brief's formula {@code Impact x Urgency x Criticality}
 *   <li>{@link WeightedScorePriorityStrategy} - weighted average that also counts affected users
 * </ul>
 *
 * Implementations must be stateless and side-effect free so that one instance can be shared by
 * every request and background job.
 */
public interface PriorityStrategy extends NamedStrategy {

  PriorityAssessment assess(PriorityFactors factors);
}
