package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;

public class AirportNotFoundException extends DomainException {

  public AirportNotFoundException(String iataCode) {
    super(ErrorCode.AIRPORT_NOT_FOUND, "No airport with IATA code " + iataCode);
  }

}
