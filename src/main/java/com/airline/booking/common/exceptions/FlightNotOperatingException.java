package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import java.time.LocalDate;

public class FlightNotOperatingException extends DomainException {

  public FlightNotOperatingException(String flightNumber, LocalDate date, String reason) {
    super(ErrorCode.FLIGHT_NOT_OPERATING,
        "Flight " + flightNumber + " does not operate on " + date + ": " + reason);
  }

}
