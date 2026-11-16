package com.servicedesk.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.servicedesk.api.dto.ErrorResponse;
import com.servicedesk.domain.exception.DomainException;
import com.servicedesk.domain.exception.DuplicateIncidentException;
import com.servicedesk.domain.exception.EntityNotFoundException;
import com.servicedesk.domain.exception.InvalidCredentialsException;
import com.servicedesk.domain.exception.InvalidIncidentException;
import com.servicedesk.domain.exception.InvalidInputException;
import com.servicedesk.domain.exception.InvalidStateTransitionException;
import com.servicedesk.domain.exception.NotificationDeliveryException;
import com.servicedesk.domain.exception.ServiceNotFoundException;
import com.servicedesk.domain.exception.SlaConfigurationMissingException;
import com.servicedesk.domain.exception.TechnicianUnavailableException;
import com.servicedesk.domain.exception.UnauthorizedOperationException;
import com.servicedesk.domain.exception.WorkloadExceededException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@DisplayName("Error handling: exceptions to HTTP responses")
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(
          Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC));
  private final MockHttpServletRequest request =
      new MockHttpServletRequest("POST", "/api/v1/incidents/INC1/close");

  @Test
  @DisplayName(
      "TC-113 every exception scenario in the brief maps to a precise HTTP status and stable"
          + " code; database failures and concurrent edits never leak internals")
  void mapping() {
    Map<DomainException, HttpStatus> expected =
        Map.ofEntries(
            Map.entry(new InvalidCredentialsException(), HttpStatus.UNAUTHORIZED),
            Map.entry(new UnauthorizedOperationException("x"), HttpStatus.FORBIDDEN),
            Map.entry(new EntityNotFoundException("x"), HttpStatus.NOT_FOUND),
            Map.entry(new ServiceNotFoundException("x"), HttpStatus.NOT_FOUND),
            Map.entry(new InvalidStateTransitionException("NEW", "CLOSE"), HttpStatus.CONFLICT),
            Map.entry(new TechnicianUnavailableException("x"), HttpStatus.CONFLICT),
            Map.entry(new WorkloadExceededException("x"), HttpStatus.CONFLICT),
            Map.entry(new DuplicateIncidentException("x"), HttpStatus.CONFLICT),
            Map.entry(new SlaConfigurationMissingException("x"), HttpStatus.INTERNAL_SERVER_ERROR),
            Map.entry(new NotificationDeliveryException("x"), HttpStatus.BAD_GATEWAY),
            Map.entry(new InvalidInputException("x"), HttpStatus.BAD_REQUEST),
            Map.entry(new InvalidIncidentException("x"), HttpStatus.BAD_REQUEST));
    expected.forEach(
        (ex, status) -> {
          ResponseEntity<ErrorResponse> r = handler.domain(ex, request);
          assertThat(r.getStatusCode()).as(ex.code()).isEqualTo(status);
          assertThat(r.getBody()).isNotNull();
          assertThat(r.getBody().code()).isEqualTo(ex.code());
          assertThat(r.getBody().path()).isEqualTo("/api/v1/incidents/INC1/close");
        });

    // database failure: 503 with a generic message, no SQL or stack trace
    ResponseEntity<ErrorResponse> db =
        handler.database(
            new DataAccessResourceFailureException("Connection refused: db-host:5432"), request);
    assertThat(db.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(db.getBody().code()).isEqualTo("DATABASE_UNAVAILABLE");
    assertThat(db.getBody().message()).doesNotContain("db-host").doesNotContain("5432");

    // two people saved the same incident at the same time
    ResponseEntity<ErrorResponse> race =
        handler.concurrentChange(
            new ObjectOptimisticLockingFailureException("IncidentEntity", "INC1"), request);
    assertThat(race.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(race.getBody().code()).isEqualTo("CONCURRENT_MODIFICATION");

    // anything unexpected: 500 with a safe message
    ResponseEntity<ErrorResponse> boom =
        handler.unexpected(new IllegalStateException("NullPointer in FooService"), request);
    assertThat(boom.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(boom.getBody().message()).doesNotContain("FooService");
  }
}
