package com.servicedesk.infrastructure.persistence;

import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.incident.IncidentStatus;
import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.SystemActor;
import com.servicedesk.domain.user.Technician;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>Repository pattern</b> implementation of the domain {@link UserRepository} on top of Spring
 * Data JPA. The domain only sees the interface; this class is the only place that knows users live
 * in a {@code users} table.
 *
 * <p>Technician workload is recomputed from incidents on every load (one grouped query for a list
 * of users), so it always reflects the database, whoever changed it.
 */
@Repository
@Transactional
public class JpaUserRepository implements UserRepository {

  /** Statuses in which an incident counts towards its technician's workload. */
  static final List<String> WORKLOAD_STATUSES =
      List.of(
          IncidentStatus.ASSIGNED.name(),
          IncidentStatus.ACKNOWLEDGED.name(),
          IncidentStatus.IN_PROGRESS.name(),
          IncidentStatus.AWAITING_INFO.name());

  private final SpringDataUserRepository users;
  private final SpringDataIncidentRepository incidents;
  private final DatabaseSequences sequences;

  public JpaUserRepository(
      SpringDataUserRepository users,
      SpringDataIncidentRepository incidents,
      DatabaseSequences sequences) {
    this.users = users;
    this.incidents = incidents;
    this.sequences = sequences;
  }

  @Override
  public User save(User user) {
    if (user.role() == Role.SYSTEM) {
      throw new InvalidInputException("The SYSTEM actor is not stored");
    }
    users
        .findByEmailIgnoreCase(user.email())
        .filter(other -> !other.id.equals(user.id().value()))
        .ifPresent(
            other -> {
              throw new InvalidInputException("E-mail " + user.email() + " is already registered");
            });
    UserEntity entity =
        users.findById(user.id().value()).orElseGet(() -> new UserEntity(user.id().value()));
    UserMapper.copy(user, entity);
    users.save(entity);
    return user;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> find(UserId id) {
    if (SystemActor.INSTANCE.id().equals(id)) {
      return Optional.of(SystemActor.INSTANCE);
    }
    return users.findById(id.value()).map(this::withSingleWorkload);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<User> findByEmail(String email) {
    return users.findByEmailIgnoreCase(email.strip()).map(this::withSingleWorkload);
  }

  @Override
  @Transactional(readOnly = true)
  public List<User> findAll() {
    return withWorkloads(users.findAllByOrderById(), Function.identity());
  }

  @Override
  @Transactional(readOnly = true)
  public List<Technician> findTechnicians() {
    List<UserEntity> rows =
        users.findByRoleInAndActiveTrueOrderById(
            List.of(Role.TECHNICIAN.name(), Role.TEAM_LEAD.name()));
    return withWorkloads(rows, u -> (Technician) u);
  }

  @Override
  @Transactional(readOnly = true)
  public List<User> withPermission(Permission permission) {
    return findAll().stream().filter(u -> u.can(permission)).toList();
  }

  @Override
  public UserId nextId(Role role) {
    String prefix =
        switch (role) {
          case EMPLOYEE -> "E";
          case TECHNICIAN -> "T";
          case TEAM_LEAD -> "L";
          case ADMINISTRATOR -> "A";
          case SYSTEM -> throw new InvalidInputException("The SYSTEM actor has a fixed id");
        };
    return new UserId(prefix + sequences.next("user_seq"));
  }

  private User withSingleWorkload(UserEntity e) {
    User u = UserMapper.toDomain(e);
    if (u instanceof Technician t) {
      t.restoreWorkload((int) incidents.countByAssigneeIdAndStatusIn(e.id, WORKLOAD_STATUSES));
    }
    return u;
  }

  private <T extends User> List<T> withWorkloads(List<UserEntity> rows, Function<User, T> cast) {
    Map<String, Integer> open = new HashMap<>();
    for (Object[] row : incidents.countOpenByAssignee(WORKLOAD_STATUSES)) {
      open.put((String) row[0], ((Number) row[1]).intValue());
    }
    return rows.stream()
        .map(
            e -> {
              User u = UserMapper.toDomain(e);
              if (u instanceof Technician t) {
                t.restoreWorkload(open.getOrDefault(e.id, 0));
              }
              return cast.apply(u);
            })
        .toList();
  }
}
