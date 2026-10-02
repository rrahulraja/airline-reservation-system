package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import java.time.LocalDate;

public class DateOutOfBookingWindowException extends DomainException {

  public DateOutOfBookingWindowException(LocalDate requested, LocalDate earliest, LocalDate latest) {
    super(ErrorCode.DATE_OUT_OF_BOOKING_WINDOW,
        "Date " + requested + " is outside the bookable window "
            + earliest + " to " + latest);
  }

}
