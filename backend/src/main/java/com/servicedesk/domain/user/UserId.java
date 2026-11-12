package com.servicedesk.domain.user;

import com.servicedesk.domain.common.Require;

/** Identity of a user, e.g. {@code E102} or {@code T205}. Immutable value object. */
public record UserId(String value) {

  public UserId {
    value = Require.notBlank(value, "User id");
  }

  @Override
  public String toString() {
    return value;
  }
}
