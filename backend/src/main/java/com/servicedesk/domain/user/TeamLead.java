package com.servicedesk.domain.user;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * A senior technician who also assigns work and receives escalations.
 *
 * <p>A team lead <i>is a</i> technician (it can still be assigned incidents), so inheriting from
 * {@link Technician} is a genuine is-a relationship. It only widens the permission set.
 */
public class TeamLead extends Technician {

  private static final Set<Permission> PERMISSIONS =
      EnumSet.of(
          Permission.REPORT_INCIDENT,
          Permission.WORK_ON_INCIDENTS,
          Permission.CLASSIFY_INCIDENTS,
          Permission.ASSIGN_INCIDENTS,
          Permission.VIEW_ALL_INCIDENTS,
          Permission.VIEW_REPORTS,
          Permission.RECEIVE_ESCALATIONS);

  public TeamLead(
      UserId id,
      String fullName,
      String email,
      String passwordHash,
      Collection<Skill> skills,
      int experienceYears,
      int maxWorkload) {
    super(id, fullName, email, passwordHash, skills, experienceYears, maxWorkload);
  }

  @Override
  public Role role() {
    return Role.TEAM_LEAD;
  }

  @Override
  protected Set<Permission> permissions() {
    return PERMISSIONS;
  }
}
