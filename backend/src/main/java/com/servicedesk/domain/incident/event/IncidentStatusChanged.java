package com.servicedesk.domain.incident.event;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.incident.IncidentAction;
import java.util.Optional;

/**
 * A lifecycle step that has no richer event of its own: acknowledge, start work, request / provide
 * information, verify, reopen, cancel.
 */
public final class IncidentStatusChanged extends IncidentEvent {

  private final IncidentAction action;
  private final String detail;

  public IncidentStatusChanged(EventHeader header, IncidentAction action, String detail) {
    super(header);
    this.action = Require.notNull(action, "Action");
    this.detail = detail;
  }

  public IncidentAction action() {
    return action;
  }

  public Optional<String> detail() {
    return Optional.ofNullable(detail);
  }

  @Override
  public EventType type() {
    return EventType.STATUS_CHANGED;
  }

  @Override
  public String description() {
    String what =
        switch (action) {
          case ACKNOWLEDGE -> "Technician acknowledged incident";
          case START_WORK -> "Investigation started";
          case REQUEST_INFO -> "Technician requested more information";
          case PROVIDE_INFO -> "Reporter provided more information";
          case VERIFY -> "Resolution verified";
          case REOPEN -> "Incident reopened";
          case CANCEL -> "Incident cancelled";
          default -> "Incident " + action.name().toLowerCase().replace('_', ' ');
        };
    return detail == null ? what : what + ": " + detail;
  }
}
