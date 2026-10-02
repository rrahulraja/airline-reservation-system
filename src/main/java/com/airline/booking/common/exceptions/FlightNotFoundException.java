package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;

public class FlightNotFoundException extends DomainException {

  public FlightNotFoundException(String flightNumber) {
    super(ErrorCode.FLIGHT_NOT_FOUND,
        "No active flight schedule for flight number " + flightNumber);
  }

}
