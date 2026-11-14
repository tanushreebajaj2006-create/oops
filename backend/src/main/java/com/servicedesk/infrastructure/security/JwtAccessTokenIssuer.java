package com.servicedesk.infrastructure.security;

import com.servicedesk.application.port.AccessTokenIssuer;
import com.servicedesk.domain.user.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Issues signed JWTs. The token carries the user id ({@code sub}) plus role and name for the UI's
 * convenience; the server never trusts those two claims for authorisation - it reloads the user on
 * each request (see {@link ActiveUserJwtConverter}).
 */
@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

  static final String ISSUER = "servicedesk";

  private final JwtEncoder encoder;
  private final Clock clock;
  private final Duration ttl;

  public JwtAccessTokenIssuer(
      JwtEncoder encoder, Clock clock, @Value("${app.security.token-ttl:PT8H}") Duration ttl) {
    this.encoder = encoder;
    this.clock = clock;
    this.ttl = ttl;
  }

  @Override
  public AccessToken issue(User user) {
    Instant now = clock.instant();
    Instant expires = now.plus(ttl);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(user.id().value())
            .issuedAt(now)
            .expiresAt(expires)
            .claim("role", user.role().name())
            .claim("name", user.fullName())
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new AccessToken(token, expires);
  }
}
