package com.servicedesk.api;

import com.servicedesk.domain.user.UserId;
import org.springframework.security.core.Authentication;

/** Reads the signed-in user's id from the request's authentication (the JWT subject). */
final class CurrentUser {

  private CurrentUser() {}

  static UserId id(Authentication authentication) {
    return new UserId(authentication.getName());
  }
}
