package com.airline.booking.common;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The single error response shape for the entire API.
 *
 * <p>Every failure — bean validation, domain rule, security rejection, or an
 * unexpected fault — renders as this record, so a client parses one structure and
 * switches on {@code code} rather than on prose.
 *
 * <pre>
 * {
 *   "code": "SEAT_UNAVAILABLE",
 *   "message": "Seats no longer available: 12A",
 *   "details": ["12A"],
 *   "traceId": "b7f1c2e4",
 *   "timestamp": "2026-10-02T10:12:03Z"
 * }
 * </pre>
 */
public record ApiError(String code, String message, List<String> details, String traceId, Instant timestamp) {
  /**
   * Normalizes {@code details} to an immutable non-null list, so handlers and
   * tests read it unconditionally and a caller holding the original list cannot
   * mutate what gets serialized.
   */
  public ApiError {
    details = details == null ? List.of() : List.copyOf(details);
  }

  public static ApiError from(DomainException e, String traceId, Clock clock) {
    return new ApiError(e.code().name(),
        e.getMessage(),
        e.details(),
        traceId,
        clock.instant());
  }

  public static ApiError of(ErrorCode code, String message, String traceId, Clock clock) {
    return new ApiError(code.name(), message, List.of(), traceId, clock.instant());
  }

  public static ApiError of(ErrorCode code,
      String message,
      List<String> details,
      String traceId,
      Clock clock) {
    return new ApiError(code.name(), message, details, traceId, clock.instant());
  }
}