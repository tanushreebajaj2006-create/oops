package com.servicedesk.domain.exception;

/**
 * Raised when a login fails. The message is deliberately the same whether the e-mail is unknown,
 * the password is wrong or the account is deactivated, so an attacker cannot learn which accounts
 * exist.
 */
public class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", "Invalid e-mail or password");
  }
}
