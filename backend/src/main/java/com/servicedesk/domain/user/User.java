package com.servicedesk.domain.user;

import com.servicedesk.domain.common.Require;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Abstract base for every person (or automated actor) that interacts with the system.
 *
 * <p>Shows <b>abstraction</b> and <b>inheritance</b>: the shared identity data lives here, while
 * each subclass answers {@link #role()} and {@link #permissions()} in its own way
 * (<b>polymorphism</b>). Fields are private and only change through intention-revealing methods
 * (<b>encapsulation</b>). The password is stored only as a hash produced outside the domain.
 */
public abstract class User {

  private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final UserId id;
  private String fullName;
  private String email;
  private String passwordHash;
  private boolean active;

  protected User(UserId id, String fullName, String email, String passwordHash) {
    this.id = Require.notNull(id, "User id");
    rename(fullName);
    changeEmail(email);
    this.passwordHash = passwordHash;
    this.active = true;
  }

  /** The role this kind of user plays. */
  public abstract Role role();

  /** The capabilities granted to this kind of user. */
  protected abstract Set<Permission> permissions();

  public final boolean can(Permission permission) {
    return active && permissions().contains(permission);
  }

  /**
   * Guard used by domain operations.
   *
   * @throws UnauthorizedOperationException if this user lacks the permission or is deactivated
   */
  public final void requirePermission(Permission permission) {
    if (!can(permission)) {
      throw new UnauthorizedOperationException(
          role() + " " + id + " is not allowed to " + permission);
    }
  }

  public final void rename(String newName) {
    this.fullName = Require.maxLength(Require.notBlank(newName, "Full name"), 100, "Full name");
  }

  public final void changeEmail(String newEmail) {
    String value = Require.notBlank(newEmail, "Email").toLowerCase();
    Require.that(EMAIL.matcher(value).matches(), "Email is not valid: " + newEmail);
    this.email = value;
  }

  /** Replaces the stored hash. Hashing itself (BCrypt) is an infrastructure concern. */
  public final void changePasswordHash(String newHash) {
    this.passwordHash = Require.notBlank(newHash, "Password hash");
  }

  public void deactivate() {
    this.active = false;
  }

  public void activate() {
    this.active = true;
  }

  public UserId id() {
    return id;
  }

  public String fullName() {
    return fullName;
  }

  public String email() {
    return email;
  }

  public String passwordHash() {
    return passwordHash;
  }

  public boolean isActive() {
    return active;
  }

  /** Two users are the same user if they share an id (entity equality). */
  @Override
  public final boolean equals(Object o) {
    return this == o || (o instanceof User other && id.equals(other.id));
  }

  @Override
  public final int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public String toString() {
    return role() + " " + id + " (" + fullName + ")";
  }
}
