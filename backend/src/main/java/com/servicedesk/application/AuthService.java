package com.servicedesk.application;

import com.servicedesk.application.port.AccessTokenIssuer;
import com.servicedesk.application.port.AccessTokenIssuer.AccessToken;
import com.servicedesk.application.port.PasswordHasher;
import com.servicedesk.domain.audit.AuditEntry;
import com.servicedesk.domain.audit.AuditLog;
import com.servicedesk.domain.exception.InvalidCredentialsException;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.user.NewUser;
import com.servicedesk.domain.user.Role;
import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserFactory;
import com.servicedesk.domain.user.UserRepository;
import java.time.Clock;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login and self-registration.
 *
 * <p>Security rules: passwords are only ever stored as BCrypt hashes; a failed login gives the same
 * answer whether the e-mail is unknown, the password wrong or the account deactivated; new
 * passwords must be 8-72 characters with at least one letter and one digit (72 is BCrypt's input
 * limit). Self-registration always creates an <i>employee</i> - other roles are created by an
 * administrator.
 */
@Service
@Transactional
public class AuthService {

  private static final Pattern STRONG_ENOUGH = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,72}$");

  private final UserRepository users;
  private final PasswordHasher hasher;
  private final AccessTokenIssuer tokens;
  private final AuditLog auditLog;
  private final Clock clock;

  public AuthService(
      UserRepository users,
      PasswordHasher hasher,
      AccessTokenIssuer tokens,
      AuditLog auditLog,
      Clock clock) {
    this.users = users;
    this.hasher = hasher;
    this.tokens = tokens;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  /** A successful login or registration: who it is and the token to send with every request. */
  public record Session(User user, AccessToken token) {}

  /** Self-registration form. */
  public record RegisterCommand(
      String fullName, String email, String password, String department) {}

  @Transactional(readOnly = true)
  public Session login(String email, String password) {
    if (email == null || password == null) {
      throw new InvalidCredentialsException();
    }
    User user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
    if (!user.isActive()
        || user.passwordHash() == null
        || !hasher.matches(password, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    return new Session(user, tokens.issue(user));
  }

  public Session register(RegisterCommand c) {
    requireAcceptablePassword(c.password());
    User user =
        UserFactory.create(
            new NewUser(
                users.nextId(Role.EMPLOYEE),
                Role.EMPLOYEE,
                c.fullName(),
                c.email(),
                hasher.hash(c.password()),
                c.department(),
                null,
                0,
                null));
    users.save(user);
    auditLog.append(
        AuditEntry.adminAction(
            user, "USER_REGISTERED", user.id() + " registered as EMPLOYEE", clock.instant()));
    return new Session(user, tokens.issue(user));
  }

  static void requireAcceptablePassword(String password) {
    if (password == null || !STRONG_ENOUGH.matcher(password).matches()) {
      throw new InvalidInputException(
          "Password must be 8-72 characters and contain at least one letter and one digit");
    }
  }
}
