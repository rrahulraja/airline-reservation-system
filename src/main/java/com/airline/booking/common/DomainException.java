package com.airline.booking.common;

import java.util.List;


/**
 * Base class for every expected business failure in the domain.
 *
 * <p>Carries a stable {@link ErrorCode} so the HTTP status is chosen in one place,
 * and an optional {@code details} list for the machine-readable payload a client
 * needs in order to react — for example the seat labels lost in a
 * {@code SEAT_UNAVAILABLE} conflict.
 *
 * <p>Extends {@link RuntimeException} deliberately. Beyond keeping {@code throws}
 * clauses out of service signatures, Spring's {@code @Transactional} rolls back on
 * unchecked exceptions but <em>commits</em> on checked ones, so a checked exception
 * here could leave a half-written booking committed.
 */
public class DomainException extends RuntimeException {
  private final ErrorCode code;
  private final List<String> details;

  public DomainException(ErrorCode code, String message) {
    this(code, message, List.of(), null);
  }

  public DomainException(ErrorCode code, String message, List<String> details) {
    this(code, message, details, null);
  }

  public DomainException(ErrorCode code, String message, Throwable cause) {
    this(code, message, List.of(), cause);
  }

  public DomainException(ErrorCode code,
      String message,
      List<String> details,
      Throwable cause) {
    super(message, cause);
    if (code == null) {
      throw new IllegalArgumentException("ErrorCode is required");
    }
    this.code = code;
    this.details = details == null ? List.of() : List.copyOf(details);
  }

  public ErrorCode code() {
    return code;
  }

  /** Machine-readable detail entries. Never null, always immutable. */
  public List<String> details() {
    return details;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName()
        + "[code=" + code
        + ", message=" + getMessage()
        + ", details=" + details
        + "]";
  }
}
