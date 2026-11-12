package com.servicedesk.domain.incident.state;

import com.servicedesk.domain.incident.IncidentAction;
import com.servicedesk.domain.incident.IncidentStatus;
import java.util.EnumSet;

/** Fix applied; the reporter must confirm it works, or reopen it. */
final class ResolvedState extends AbstractIncidentState {

  static final ResolvedState INSTANCE = new ResolvedState();

  private ResolvedState() {
    super(IncidentStatus.RESOLVED, EnumSet.of(IncidentAction.VERIFY, IncidentAction.REOPEN));
  }

  @Override
  public IncidentState verify() {
    return VerifiedState.INSTANCE;
  }

  @Override
  public IncidentState reopen() {
    return InProgressState.INSTANCE;
  }
}
