package com.servicedesk.domain.sla;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.Incident;
import com.servicedesk.domain.priority.Priority;
import com.servicedesk.domain.user.Role;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

/**
 * Decides how far up the chain an unacknowledged critical incident must go (the brief's "Automatic
 * Escalation": Technician -> Team Lead -> Administrator / IT Manager).
 *
 * <p>Rule: a P1 or P2 incident that nobody has acknowledged is escalated
 *
 * <ul>
 *   <li>to <b>level 1 (team lead)</b> as soon as its response SLA is breached;
 *   <li>to <b>level 2 (administrator / IT manager)</b> once it has been overdue for another full
 *       response period (e.g. a P1 with a 5-minute response target, still unacknowledged after 10
 *       minutes).
 * </ul>
 *
 * Lower priorities are never escalated automatically - their SLA warnings are enough.
 */
public final class EscalationPolicy {

  /** Who is responsible at each level; index = level. */
  private static final Role[] CHAIN = {Role.TECHNICIAN, Role.TEAM_LEAD, Role.ADMINISTRATOR};

  private final Set<Priority> escalatable;

  public EscalationPolicy() {
    this(EnumSet.of(Priority.P1, Priority.P2));
  }

  public EscalationPolicy(Set<Priority> escalatable) {
    Require.notNull(escalatable, "Escalatable priorities");
    this.escalatable = Set.copyOf(escalatable);
  }

  /** The level the incident should be at right now (0 = no escalation needed). */
  public int requiredLevel(Incident incident, SlaSnapshot snapshot) {
    Require.notNull(incident, "Incident");
    Require.notNull(snapshot, "SLA snapshot");
    if (!incident.isOpen()
        || incident.acknowledgedAt().isPresent()
        || !escalatable.contains(snapshot.priority())) {
      return 0;
    }
    SlaMeasure response = snapshot.response();
    if (response.status() != SlaStatus.BREACHED) {
      return 0;
    }
    Duration allowed = Duration.between(response.startedAt(), response.dueAt());
    Duration overdue = response.overdue(snapshot.evaluatedAt());
    return overdue.compareTo(allowed) >= 0 ? 2 : 1;
  }

  /** Role that takes over at the given level. */
  public static Role roleForLevel(int level) {
    Require.inRange(level, 0, CHAIN.length - 1, "Escalation level");
    return CHAIN[level];
  }

  public static int maxLevel() {
    return CHAIN.length - 1;
  }
}
