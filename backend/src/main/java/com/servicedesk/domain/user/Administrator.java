package com.servicedesk.domain.user;

import java.util.EnumSet;
import java.util.Set;

/** IT manager / administrator: configures the system and oversees every incident. */
public class Administrator extends User {

  private static final Set<Permission> PERMISSIONS = EnumSet.allOf(Permission.class);

  public Administrator(UserId id, String fullName, String email, String passwordHash) {
    super(id, fullName, email, passwordHash);
  }

  @Override
  public Role role() {
    return Role.ADMINISTRATOR;
  }

  @Override
  protected Set<Permission> permissions() {
    return PERMISSIONS;
  }
}
