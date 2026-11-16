package com.servicedesk.domain.user;

import java.util.EnumSet;
import java.util.Set;

/**
 * The automated actor used for actions the system performs by itself (auto-classification,
 * auto-assignment, SLA escalation). Having it as a {@link User} means every audit entry always has
 * an actor, and no {@code null} checks are needed.
 */
public final class SystemActor extends User {

  public static final SystemActor INSTANCE = new SystemActor();

  private static final Set<Permission> PERMISSIONS =
      EnumSet.of(
          Permission.CLASSIFY_INCIDENTS,
          Permission.ASSIGN_INCIDENTS,
          Permission.VIEW_ALL_INCIDENTS,
          Permission.CONFIGURE_SYSTEM);

  private SystemActor() {
    super(new UserId("SYSTEM"), "ServiceDesk Automation", "system@servicedesk.local", null);
  }

  @Override
  public Role role() {
    return Role.SYSTEM;
  }

  @Override
  protected Set<Permission> permissions() {
    return PERMISSIONS;
  }

  @Override
  public void deactivate() {
    throw new UnsupportedOperationException("The system actor cannot be deactivated");
  }
}
