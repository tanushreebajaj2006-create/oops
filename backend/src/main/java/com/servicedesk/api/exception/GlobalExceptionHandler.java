package com.servicedesk.api.exception;

import com.servicedesk.api.dto.ErrorResponse;
import com.servicedesk.domain.exception.DomainException;
import com.servicedesk.domain.exception.DuplicateIncidentException;
import com.servicedesk.domain.exception.EntityNotFoundException;
import com.servicedesk.domain.exception.InvalidCredentialsException;
import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.exception.NotificationDeliveryException;
import com.servicedesk.domain.exception.ServiceNotFoundException;
import com.servicedesk.domain.exception.SlaConfigurationMissingException;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.WorkloadExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Translates every failure into the one {@link ErrorResponse} JSON shape with the right HTTP
 * status. Domain exceptions keep their stable {@code code}, so the frontend can react precisely
 * (e.g. show "this incident was changed by someone else, reload"). Stack traces and internal
 * details never reach the client.
 *
 * <table>
 *   <caption>Status mapping</caption>
 *   <tr><td>400</td><td>invalid input, invalid incident data, malformed JSON, bad parameters</td></tr>
 *   <tr><td>401</td><td>invalid login</td></tr>
 *   <tr><td>403</td><td>unauthorised operation</td></tr>
 *   <tr><td>404</td><td>incident / user / service / category not found</td></tr>
 *   <tr><td>409</td><td>invalid state transition, technician unavailable, workload exceeded,
 *       duplicate incident, concurrent modification, constraint violation</td></tr>
 *   <tr><td>500</td><td>missing SLA configuration, unexpected errors</td></tr>
 *   <tr><td>502</td><td>notification provider failure</td></tr>
 *   <tr><td>503</td><td>database unavailable</td></tr>
 * </table>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ErrorResponse> domain(DomainException ex, HttpServletRequest request) {
    HttpStatus status = statusFor(ex);
    if (status.is5xxServerError()) {
      LOG.error("{} on {}: {}", ex.code(), request.getRequestURI(), ex.getMessage());
    }
    return respond(status, ex.code(), ex.getMessage(), request);
  }

  static HttpStatus statusFor(DomainException ex) {
    return switch (ex) {
      case InvalidCredentialsException e -> HttpStatus.UNAUTHORIZED;
      case UnauthorizedOperationException e -> HttpStatus.FORBIDDEN;
      case EntityNotFoundException e -> HttpStatus.NOT_FOUND;
      case ServiceNotFoundException e -> HttpStatus.NOT_FOUND;
      case InvalidStateTransitionException e -> HttpStatus.CONFLICT;
      case TechnicianUnavailableException e -> HttpStatus.CONFLICT;
      case WorkloadExceededException e -> HttpStatus.CONFLICT;
      case DuplicateIncidentException e -> HttpStatus.CONFLICT;
      case SlaConfigurationMissingException e -> HttpStatus.INTERNAL_SERVER_ERROR;
      case NotificationDeliveryException e -> HttpStatus.BAD_GATEWAY;
      default -> HttpStatus.BAD_REQUEST; // InvalidInput, InvalidIncident
    };
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidBody(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .orElse("Validation failed");
    return respond(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message, request);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> invalidParameter(
      ConstraintViolationException ex, HttpServletRequest request) {
    return respond(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage(), request);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  public ResponseEntity<ErrorResponse> unreadable(Exception ex, HttpServletRequest request) {
    String message =
        ex instanceof MethodArgumentTypeMismatchException m
            ? "Invalid value '" + m.getValue() + "' for parameter '" + m.getName() + "'"
            : ex instanceof MissingServletRequestParameterException p
                ? "Missing parameter '" + p.getParameterName() + "'"
                : "Request body is missing or is not valid JSON";
    return respond(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message, request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> accessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return respond(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "Your role is not allowed to do this", request);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> concurrentChange(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    return respond(
        HttpStatus.CONFLICT,
        "CONCURRENT_MODIFICATION",
        "Someone else changed this record at the same time. Reload and try again.",
        request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> integrity(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    LOG.warn(
        "Constraint violation on {}: {}",
        request.getRequestURI(),
        ex.getMostSpecificCause().getMessage());
    return respond(
        HttpStatus.CONFLICT, "DATA_CONFLICT", "The change conflicts with existing data", request);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ErrorResponse> database(
      DataAccessException ex, HttpServletRequest request) {
    LOG.error("Database failure on {}", request.getRequestURI(), ex);
    return respond(
        HttpStatus.SERVICE_UNAVAILABLE,
        "DATABASE_UNAVAILABLE",
        "The database is temporarily unavailable. Please try again shortly.",
        request);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> methodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", ex.getMessage(), request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> noRoute(
      NoResourceFoundException ex, HttpServletRequest request) {
    return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "No such endpoint", request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception ex, HttpServletRequest request) {
    LOG.error("Unhandled exception on {}", request.getRequestURI(), ex);
    return respond(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "INTERNAL_ERROR",
        "An unexpected internal error occurred. Please contact system support.",
        request);
  }

  private ResponseEntity<ErrorResponse> respond(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI()));
  }
}
