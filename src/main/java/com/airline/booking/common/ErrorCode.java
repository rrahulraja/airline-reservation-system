package com.airline.booking.common;

import org.springframework.http.HttpStatus;

/**
 * Every machine-readable error code the API can return, each carrying its HTTP
 * status.
 *
 * <p>Putting the status on the code rather than at the throw site means the
 * mapping exists in exactly one place. A client can switch on the code string and
 * never has to parse a message.
 *
 * <p>Bucket semantics:
 * <ul>
 *   <li>400 — malformed or self-inconsistent input</li>
 *   <li>401/403 — not authenticated / not permitted</li>
 *   <li>404 — unknown resource</li>
 *   <li>409 — state or uniqueness conflict</li>
 *   <li>422 — well-formed input rejected by a business rule</li>
 * </ul>
 */
public enum ErrorCode {

  // 400 - the request itself is wrong
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
  INVALID_SEAT_LABEL(HttpStatus.BAD_REQUEST),

  // 401 / 403
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
  FORBIDDEN(HttpStatus.FORBIDDEN),

  // 404 - the thing referenced does not exist
  FLIGHT_NOT_FOUND(HttpStatus.NOT_FOUND),
  BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND),
  AIRPORT_NOT_FOUND(HttpStatus.NOT_FOUND),
  AIRCRAFT_NOT_FOUND(HttpStatus.NOT_FOUND),

  // 409 - conflicts with existing state
  SEAT_UNAVAILABLE(HttpStatus.CONFLICT),
  BOOKING_NOT_CANCELLABLE(HttpStatus.CONFLICT),
  BOOKING_NOT_CONFIRMABLE(HttpStatus.CONFLICT),
  HOLD_EXPIRED(HttpStatus.CONFLICT),
  DUPLICATE_FLIGHT_NUMBER(HttpStatus.CONFLICT),

  // 422 - valid request, business rule says no
  FLIGHT_NOT_OPERATING(HttpStatus.UNPROCESSABLE_ENTITY),
  DATE_OUT_OF_BOOKING_WINDOW(HttpStatus.UNPROCESSABLE_ENTITY),

  // 500
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus httpStatus;

  ErrorCode(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
  }

  public HttpStatus httpStatus() {
    return httpStatus;
  }
}
