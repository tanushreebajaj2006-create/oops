package com.servicedesk.domain.user;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.InvalidInputException;
import java.util.List;

/**
 * <b>Factory pattern</b>: the single place that decides which {@link User} subclass to build for a
 * given {@link Role}. Callers (registration, admin screens, seed data) never call subclass
 * constructors directly, so adding a new role only touches this class.
 */
public final class UserFactory {

  private UserFactory() {}

  public static User create(NewUser request) {
    Require.notNull(request, "User details");
    Role role = Require.notNull(request.role(), "Role");
    List<Skill> skills = request.skills() == null ? List.of() : request.skills();
    int maxWorkload =
        request.maxWorkload() == null ? Technician.DEFAULT_MAX_WORKLOAD : request.maxWorkload();

    return switch (role) {
      case EMPLOYEE ->
          new Employee(
              request.id(),
              request.fullName(),
              request.email(),
              request.passwordHash(),
              request.department());
      case TECHNICIAN ->
          new Technician(
              request.id(),
              request.fullName(),
              request.email(),
              request.passwordHash(),
              skills,
              request.experienceYears(),
              maxWorkload);
      case TEAM_LEAD ->
          new TeamLead(
              request.id(),
              request.fullName(),
              request.email(),
              request.passwordHash(),
              skills,
              request.experienceYears(),
              maxWorkload);
      case ADMINISTRATOR ->
          new Administrator(
              request.id(), request.fullName(), request.email(), request.passwordHash());
      case SYSTEM -> throw new InvalidInputException("The SYSTEM user cannot be created");
    };
  }
}
