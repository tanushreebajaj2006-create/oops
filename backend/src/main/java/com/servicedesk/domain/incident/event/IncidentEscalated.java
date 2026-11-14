package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.UserId;

/**
 * An unacknowledged or overdue incident was escalated up the chain (Technician -> Team Lead ->
 * Administrator -> IT Manager). Raised by the escalation job added in Phase 5.
 */
public final class IncidentEscalated extends IncidentEvent {

  private final int level;
  private final UserId escalatedTo;
  private final Role escalatedToRole;
  private final String reason;

  public IncidentEscalated(
      EventHeader header, int level, UserId escalatedTo, Role escalatedToRole, String reason) {
    super(header);
    this.level = Require.inRange(level, 1, 10, "Escalation level");
    this.escalatedTo = Require.notNull(escalatedTo, "Escalation target");
    this.escalatedToRole = Require.notNull(escalatedToRole, "Escalation target role");
    this.reason = Require.notBlank(reason, "Escalation reason");
  }

  public int level() {
    return level;
  }

  public UserId escalatedTo() {
    return escalatedTo;
  }

  public Role escalatedToRole() {
    return escalatedToRole;
  }

  public String reason() {
    return reason;
  }

  @Override
  public EventType type() {
    return EventType.ESCALATED;
  }

  @Override
  public String description() {
    return "Escalated (level %d) to %s %s: %s"
        .formatted(level, escalatedToRole, escalatedTo, reason);
  }
}
