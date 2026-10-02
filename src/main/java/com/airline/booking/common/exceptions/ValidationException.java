package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import java.util.List;

public class ValidationException extends DomainException {

  public ValidationException(String message) {
    super(ErrorCode.VALIDATION_ERROR, message);
  }

  public ValidationException(String message, List<String> details) {
    super(ErrorCode.VALIDATION_ERROR, message, details);
  }

}
