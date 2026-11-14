package com.servicedesk.application;

import com.servicedesk.application.port.PasswordHasher;
import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.user.Employee;
import com.servicedesk.domain.user.NewUser;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.Skill;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserFactory;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User administration (by administrators) and self-service profile actions. */
@Service
@Transactional
public class UserService {

  private final UserRepository users;
  private final PasswordHasher hasher;
  private final AuditLog auditLog;
  private final Clock clock;

  public UserService(UserRepository users, PasswordHasher hasher, AuditLog auditLog, Clock clock) {
    this.users = users;
    this.hasher = hasher;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  /** New account of any role, created by an administrator. */
  public record CreateUserCommand(
      Role role,
      String fullName,
      String email,
      String password,
      String department,
      String phone,
      Map<String, Integer> skills,
      int experienceYears,
      Integer maxWorkload) {}

  /** Partial update: {@code null} fields are left unchanged. */
  public record UpdateUserCommand(
      String fullName,
      String phone,
      Boolean active,
      String department,
      Map<String, Integer> skills,
      Integer experienceYears,
      Integer maxWorkload,
      Boolean available) {}

  @Transactional(readOnly = true)
  public User me(UserId id) {
    return users.get(id);
  }

  @Transactional(readOnly = true)
  public List<User> list(UserId adminId) {
    admin(adminId, Permission.MANAGE_USERS);
    return users.findAll();
  }

  /** Technicians and team leads with their current workload (for supervisors). */
  @Transactional(readOnly = true)
  public List<Technician> technicians(UserId viewerId) {
    User viewer = users.get(viewerId);
    if (!viewer.can(Permission.ASSIGN_INCIDENTS) && !viewer.can(Permission.VIEW_REPORTS)) {
      throw new UnauthorizedOperationException(viewerId + " cannot view the technician list");
    }
    return users.findTechnicians();
  }

  public User create(UserId adminId, CreateUserCommand c) {
    User admin = admin(adminId, Permission.MANAGE_USERS);
    if (c.role() == null || c.role() == Role.SYSTEM) {
      throw new InvalidInputException("A valid role is required");
    }
    AuthService.requireAcceptablePassword(c.password());
    User user =
        UserFactory.create(
            new NewUser(
                users.nextId(c.role()),
                c.role(),
                c.fullName(),
                c.email(),
                hasher.hash(c.password()),
                c.department(),
                toSkills(c.skills()),
                c.experienceYears(),
                c.maxWorkload()));
    user.changePhone(c.phone());
    users.save(user);
    audit(
        admin,
        "USER_CREATED",
        "Created " + user.role() + " " + user.id() + " (" + user.email() + ")");
    return user;
  }

  public User update(UserId adminId, UserId targetId, UpdateUserCommand c) {
    User admin = admin(adminId, Permission.MANAGE_USERS);
    User user = users.get(targetId);
    List<String> changes = new ArrayList<>();
    if (c.fullName() != null) {
      user.rename(c.fullName());
      changes.add("name");
    }
    if (c.phone() != null) {
      user.changePhone(c.phone());
      changes.add("phone");
    }
    if (c.active() != null && c.active() != user.isActive()) {
      if (!c.active() && user.equals(admin)) {
        throw new InvalidInputException("You cannot deactivate your own account");
      }
      if (c.active()) {
        user.activate();
      } else {
        user.deactivate();
      }
      changes.add(c.active() ? "activated" : "deactivated");
    }
    if (c.department() != null && user instanceof Employee e) {
      e.moveToDepartment(c.department());
      changes.add("department");
    }
    if (user instanceof Technician t) {
      if (c.experienceYears() != null) {
        t.setExperienceYears(c.experienceYears());
        changes.add("experience");
      }
      if (c.maxWorkload() != null) {
        t.setMaxWorkload(c.maxWorkload());
        changes.add("max workload " + c.maxWorkload());
      }
      if (c.available() != null) {
        setAvailable(t, c.available());
        changes.add(c.available() ? "available" : "unavailable");
      }
      if (c.skills() != null) {
        t.skills().stream().map(Skill::categoryCode).toList().forEach(t::removeSkill);
        toSkills(c.skills()).forEach(t::addSkill);
        changes.add("skills");
      }
    }
    users.save(user);
    if (!changes.isEmpty()) {
      audit(admin, "USER_UPDATED", "Updated " + user.id() + ": " + String.join(", ", changes));
    }
    return user;
  }

  /** A technician marks themself available or away (holiday, sick leave). */
  public Technician setOwnAvailability(UserId technicianId, boolean available) {
    if (!(users.get(technicianId) instanceof Technician t)) {
      throw new UnauthorizedOperationException("Only technicians have an availability status");
    }
    setAvailable(t, available);
    users.save(t);
    audit(
        t, "AVAILABILITY_CHANGED", t.id() + " is now " + (available ? "available" : "unavailable"));
    return t;
  }

  private static void setAvailable(Technician t, boolean available) {
    if (available) {
      t.markAvailable();
    } else {
      t.markUnavailable();
    }
  }

  private static List<Skill> toSkills(Map<String, Integer> skills) {
    if (skills == null) {
      return List.of();
    }
    return skills.entrySet().stream().map(e -> new Skill(e.getKey(), e.getValue())).toList();
  }

  private User admin(UserId id, Permission permission) {
    User user = users.get(id);
    user.requirePermission(permission);
    return user;
  }

  private void audit(User actor, String action, String description) {
    auditLog.append(AuditEntry.adminAction(actor, action, description, clock.instant()));
  }
}
