package com.servicedesk.domain.user;

import com.servicedesk.domain.exception.EntityNotFoundException;
import java.util.List;
import java.util.Optional;

/**
 * <b>Repository</b> of users. Extends the narrow {@link UserDirectory} so it can be handed to
 * anything that only needs look-ups (notifications, escalation).
 *
 * <p>A loaded {@link Technician}'s workload is always recomputed from the incidents currently
 * assigned to them, so it can never drift out of step with reality.
 */
public interface UserRepository extends UserDirectory {

  User save(User user);

  /**
   * @throws EntityNotFoundException if there is no such user
   */
  default User get(UserId id) {
    return find(id).orElseThrow(() -> new EntityNotFoundException("User " + id + " not found"));
  }

  Optional<User> findByEmail(String email);

  List<User> findAll();

  /** Active technicians and team leads, with up-to-date workload - the assignment pool. */
  List<Technician> findTechnicians();

  /** Next free id for a new user, prefixed by role: E (employee), T, L (team lead), A. */
  UserId nextId(Role role);
}
