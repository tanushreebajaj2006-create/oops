package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.user.Employee;
import com.servicedesk.domain.user.NewUser;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.Skill;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserFactory;
import com.servicedesk.domain.user.UserId;
import java.util.Comparator;
import java.util.List;

/**
 * Converts between {@link UserEntity} rows and domain {@link User} objects. Rebuilding goes through
 * the domain's own {@link UserFactory}, so a loaded user is validated exactly like a newly
 * registered one and gets the right subclass for its role.
 */
final class UserMapper {

  private UserMapper() {}

  static User toDomain(UserEntity e) {
    List<Skill> skills =
        e.skills.stream()
            .map(s -> new Skill(s.categoryCode, s.proficiency))
            .sorted(Comparator.comparing(Skill::categoryCode))
            .toList();
    User user =
        UserFactory.create(
            new NewUser(
                new UserId(e.id),
                Role.valueOf(e.role),
                e.fullName,
                e.email,
                e.passwordHash,
                e.department,
                skills,
                e.experienceYears == null ? 0 : e.experienceYears,
                e.maxWorkload));
    user.changePhone(e.phone);
    if (!e.active) {
      user.deactivate();
    }
    if (user instanceof Technician t && Boolean.FALSE.equals(e.available)) {
      t.markUnavailable();
    }
    return user;
  }

  static void copy(User u, UserEntity e) {
    e.role = u.role().name();
    e.fullName = u.fullName();
    e.email = u.email();
    e.passwordHash = u.passwordHash();
    e.phone = u.phone().orElse(null);
    e.active = u.isActive();
    e.department = u instanceof Employee emp ? emp.department() : null;
    e.skills.clear();
    if (u instanceof Technician t) {
      e.experienceYears = t.experienceYears();
      e.maxWorkload = t.maxWorkload();
      e.available = t.isAvailable();
      t.skills()
          .forEach(s -> e.skills.add(new UserEntity.SkillValue(s.categoryCode(), s.proficiency())));
    } else {
      e.experienceYears = null;
      e.maxWorkload = null;
      e.available = null;
    }
  }
}
