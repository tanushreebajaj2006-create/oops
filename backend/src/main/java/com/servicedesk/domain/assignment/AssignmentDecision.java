package com.servicedesk.domain.assignment;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentId;
import com.servicedesk.domain.user.Technician;

/**
 * Record of an automatic assignment, used for the audit trail ("Assigned to T205 by SKILL_BASED:
 * APPLICATION skill 4/5, load 0/5") and the assignment notification.
 *
 * @param incidentId the incident
 * @param technician who got it
 * @param strategy name of the strategy that chose them
 * @param reason why they were chosen
 * @param candidatesConsidered how many eligible technicians the strategy ranked
 */
public record AssignmentDecision(
    IncidentId incidentId,
    Technician technician,
    String strategy,
    String reason,
    int candidatesConsidered) {

  public AssignmentDecision {
    Require.notNull(incidentId, "Incident id");
    Require.notNull(technician, "Technician");
    Require.notBlank(strategy, "Strategy");
    Require.notBlank(reason, "Reason");
  }

  public String describe() {
    return "Assigned to %s by %s: %s".formatted(technician.id(), strategy, reason);
  }
}
