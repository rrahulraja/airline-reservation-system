package com.airline.booking.common;

import static org.springframework.http.ResponseEntity.badRequest;

import jakarta.validation.ConstraintViolationException;
import java.nio.file.AccessDeniedException;
import java.time.Clock;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Translates every exception into the single {@link ApiError} shape.
 *
 * <p>Deliberately does NOT extend {@code ResponseEntityExceptionHandler}: that
 * base class renders Spring's {@code ProblemDetail} (RFC 7807) format, which would
 * give the API two competing error shapes. Handling the few Spring exceptions that
 * matter explicitly keeps one contract.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final Clock clock;

  public GlobalExceptionHandler(Clock clock) {
    this.clock = clock;
  }

  /**
   * Every expected business failure. Logged at WARN, not ERROR: a 409 on a
   * contested seat is the system working correctly, and logging it as an error
   * trains people to ignore errors.
   */
  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ApiError> handleDomain(DomainException e) {
    String traceId = RequestIdFilter.currentOrFallback();
    log.warn("Domain failure [{}] {} details={}", e.code(), e.getMessage(), e.details());
    return ResponseEntity.status(e.code().httpStatus())
        .body(ApiError.from(e, traceId, clock));
  }

  /** {@code @Valid} on a request body failed. One details entry per field. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleBodyValidation(MethodArgumentNotValidException e) {
    List<String> details = e.getBindingResult().getFieldErrors().stream()
        .map(GlobalExceptionHandler::describeFieldError)
        .sorted()
        .collect(Collectors.toList());

    return badRequest("Request validation failed", details);
  }


  /** {@code @Validated} on a parameter or path variable failed. */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException e) {
    List<String> details = e.getConstraintViolations().stream()
        .map(v -> v.getPropertyPath() + ": " + v.getMessage())
        .sorted()
        .collect(Collectors.toList());

    return badRequest("Request validation failed", details);
  }


  /** Body was not parseable JSON, or a value could not be coerced. */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
    // Do NOT echo e.getMessage(): Jackson's message includes a fragment of the
    // request body and the target class name.
    return badRequest("Request body is missing or not valid JSON", List.of());
  }

  /** A query parameter was absent, or the wrong type (e.g. date=not-a-date). */
  @ExceptionHandler({MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiError> handleParameterProblem(Exception e) {
    String detail = e instanceof MissingServletRequestParameterException missing
        ? missing.getParameterName() + ": required parameter is missing"
        : ((MethodArgumentTypeMismatchException) e).getName() + ": value is not of the expected type";

    return badRequest("Request validation failed", List.of(detail));
  }

  /**
   * Two concurrent writers raced on the same row and the {@code @Version} check
   * lost. A conflict, not an internal fault — see Task 11.
   */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException e) {
    String traceId = RequestIdFilter.currentOrFallback();
    log.warn("Optimistic lock conflict: {}", e.getMessage());
    return ResponseEntity.status(ErrorCode.BOOKING_NOT_CANCELLABLE.httpStatus())
        .body(ApiError.of(ErrorCode.BOOKING_NOT_CANCELLABLE,
            "The booking was modified concurrently. Re-read it and retry.",
            traceId, clock));
  }


  /** Thrown by method security after the filter chain has already authenticated. */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException e) {
    String traceId = RequestIdFilter.currentOrFallback();
    return ResponseEntity.status(ErrorCode.FORBIDDEN.httpStatus())
        .body(ApiError.of(ErrorCode.FORBIDDEN,
            "You are not permitted to perform this operation",
            traceId, clock));
  }

  /**
   * Anything unanticipated. The full stack trace goes to the log, correlated by
   * traceId; the client gets the traceId and nothing else.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleUnexpected(Exception e) {
    String traceId = RequestIdFilter.currentOrFallback();
    log.error("Unhandled exception [traceId={}]", traceId, e);
    return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
        .body(ApiError.of(ErrorCode.INTERNAL_ERROR,
            "An unexpected error occurred. Quote the traceId when reporting it.",
            traceId, clock));
  }

  private ResponseEntity<ApiError> badRequest(String message, List<String> details) {
    String traceId = RequestIdFilter.currentOrFallback();
    log.warn("Validation failure: {} {}", message, details);
    return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.httpStatus())
        .body(ApiError.of(ErrorCode.VALIDATION_ERROR, message, details, traceId, clock));
  }

  private static String describeFieldError(FieldError error) {
    return error.getField() + ": " + error.getDefaultMessage();
  }


}
