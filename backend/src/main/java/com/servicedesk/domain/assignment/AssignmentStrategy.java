package com.servicedesk.domain.assignment;

import com.servicedesk.domain.common.NamedStrategy;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.user.Technician;
import java.util.List;

/**
 * <b>Strategy pattern</b>: one way of choosing which technician should handle an incident.
 *
 * <p>The {@link TechnicianAssigner} filters out technicians who cannot take work, then asks the
 * active strategy to {@link #rank} the rest. Swapping the strategy (an admin setting) changes who
 * gets picked without changing the assigner, the incident or the API.
 *
 * <p>Implementations: {@link SkillBasedStrategy}, {@link LoadBasedStrategy}, {@link
 * RoundRobinStrategy}, {@link PriorityBasedStrategy}.
 */
public interface AssignmentStrategy extends NamedStrategy {

  /**
   * Orders the eligible technicians from best to worst for this incident. A strategy may leave out
   * technicians it considers unsuitable (e.g. no matching skill); an empty list means nobody fits.
   *
   * @param incident the incident to place (classified and prioritised)
   * @param eligible technicians who are active, available and below their workload limit
   */
  List<AssignmentCandidate> rank(Incident incident, List<Technician> eligible);

  /**
   * Called after the top candidate has actually been assigned. Stateless strategies ignore it;
   * {@link RoundRobinStrategy} uses it to advance its pointer.
   */
  default void onAssigned(Technician chosen) {}
}
