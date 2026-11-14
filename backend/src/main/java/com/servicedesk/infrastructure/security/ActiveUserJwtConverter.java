package com.servicedesk.infrastructure.security;

import com.servicedesk.domain.user.Permission;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import com.servicedesk.domain.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Turns a verified JWT into the request's authentication, using the user's <b>current</b> state
 * from the database rather than whatever the token claimed when it was issued:
 *
 * <ul>
 *   <li>a deactivated (or deleted) account is rejected immediately, even with an unexpired token;
 *   <li>authorities are the user's role ({@code ROLE_TECHNICIAN}) plus every domain permission they
 *       hold ({@code ASSIGN_INCIDENTS}), so a role change takes effect on the next request.
 * </ul>
 */
@Component
public class ActiveUserJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserRepository users;

  public ActiveUserJwtConverter(UserRepository users) {
    this.users = users;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    User user =
        users
            .find(new UserId(jwt.getSubject()))
            .filter(User::isActive)
            .orElseThrow(() -> new DisabledException("Account is not active"));
    List<GrantedAuthority> authorities = new ArrayList<>();
    authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
    for (Permission p : Permission.values()) {
      if (user.can(p)) {
        authorities.add(new SimpleGrantedAuthority(p.name()));
      }
    }
    return new JwtAuthenticationToken(jwt, authorities, user.id().value());
  }
}
