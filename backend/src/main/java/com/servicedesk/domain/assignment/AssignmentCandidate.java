package com.servicedesk.domain.assignment;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.Technician;

/**
 * A technician as ranked by an {@link AssignmentStrategy}, with the reason for the ranking so the
 * UI can show "recommended because ..." and the audit trail can record why someone was chosen.
 *
 * @param technician the candidate
 * @param score strategy-specific score; higher is better
 * @param reason short human-readable explanation
 */
public record AssignmentCandidate(Technician technician, double score, String reason) {

  public AssignmentCandidate {
    Require.notNull(technician, "Technician");
    Require.notBlank(reason, "Reason");
  }
}
