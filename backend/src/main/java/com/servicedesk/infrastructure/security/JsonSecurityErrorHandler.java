package com.servicedesk.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.servicedesk.api.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers security failures with the same JSON error format as every other API error: 401 when no
 * valid token was sent, 403 when the token is valid but the role lacks the permission.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper json;
  private final Clock clock;

  public JsonSecurityErrorHandler(ObjectMapper json, Clock clock) {
    this.json = json;
    this.clock = clock;
  }

  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException {
    write(
        response,
        request,
        HttpStatus.UNAUTHORIZED,
        "UNAUTHENTICATED",
        "Sign in to continue (missing, expired or invalid token)");
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException {
    write(
        response,
        request,
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "Your role is not allowed to do this");
  }

  private void write(
      HttpServletResponse response,
      HttpServletRequest request,
      HttpStatus status,
      String code,
      String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(
        response.getOutputStream(),
        new ErrorResponse(
            Instant.now(clock),
            status.value(),
            status.getReasonPhrase(),
            code,
            message,
            request.getRequestURI()));
  }
}
