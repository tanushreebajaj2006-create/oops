package com.servicedesk.api.dto;

import java.time.Instant;

/**
 * The single JSON shape for every API error.
 *
 * @param timestamp when it happened
 * @param status HTTP status code
 * @param error HTTP reason phrase
 * @param code stable machine-readable code the frontend can switch on, e.g. {@code
 *     INVALID_STATE_TRANSITION}
 * @param message human-readable explanation, safe to show to the user
 * @param path request path
 */
public record ErrorResponse(
    Instant timestamp, int status, String error, String code, String message, String path) {}
