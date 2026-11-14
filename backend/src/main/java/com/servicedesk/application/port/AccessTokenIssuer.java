package com.servicedesk.application.port;

import com.servicedesk.domain.user.User;
import java.time.Instant;

/** Port: issues the bearer token a client sends with every request (JWT in infrastructure). */
public interface AccessTokenIssuer {

  AccessToken issue(User user);

  /** A signed token and when it stops being valid. */
  record AccessToken(String value, Instant expiresAt) {}
}
