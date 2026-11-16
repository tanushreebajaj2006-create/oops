package com.servicedesk.domain.exception;

/**
 * Root of every business-rule failure raised by the domain layer.
 *
 * <p>It is abstract on purpose: callers should always throw a specific subclass so that the API
 * layer can map each kind of failure to the right HTTP status (400, 403, 404, 409...). Every
 * subclass carries a stable {@link #code()} that the frontend can rely on.
 */
public abstract class DomainException extends RuntimeException {

  private final String code;

  protected DomainException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** Machine-readable error code, e.g. {@code INVALID_STATE_TRANSITION}. */
  public String code() {
    return code;
  }
}
