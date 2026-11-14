package com.servicedesk.domain.user;

import java.util.List;
import java.util.Optional;

/**
 * Read-only lookup of users, as needed by domain services such as notification dispatch and
 * escalation. Kept deliberately narrow (interface segregation): the full user repository of Phase 4
 * implements it, and tests can supply a tiny in-memory version.
 */
public interface UserDirectory {

  Optional<User> find(UserId id);

  /** Active users who hold the permission, e.g. everyone who receives escalations. */
  List<User> withPermission(Permission permission);
}
