package com.servicedesk.domain.support;

import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserDirectory;
import com.servicedesk.domain.user.UserId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Test {@link UserDirectory} backed by a map. */
public final class InMemoryUserDirectory implements UserDirectory {

  private final Map<UserId, User> users = new LinkedHashMap<>();

  public InMemoryUserDirectory add(User... toAdd) {
    for (User u : toAdd) {
      users.put(u.id(), u);
    }
    return this;
  }

  @Override
  public Optional<User> find(UserId id) {
    return Optional.ofNullable(users.get(id));
  }

  @Override
  public List<User> withPermission(Permission permission) {
    return users.values().stream().filter(u -> u.can(permission)).toList();
  }
}
