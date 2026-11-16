package com.servicedesk.infrastructure.security;

import com.servicedesk.application.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * {@link PasswordHasher} using BCrypt (cost 10): salted, deliberately slow, so a stolen database
 * does not reveal passwords.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

  @Override
  public String hash(String rawPassword) {
    return encoder.encode(rawPassword);
  }

  @Override
  public boolean matches(String rawPassword, String storedHash) {
    return storedHash != null && encoder.matches(rawPassword, storedHash);
  }
}
