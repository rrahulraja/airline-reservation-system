package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;

public class BookingNotFoundException extends DomainException {

  public BookingNotFoundException(String pnr) {
    super(ErrorCode.BOOKING_NOT_FOUND, "No booking found for PNR " + pnr);
  }

}
