package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import java.util.List;

public class SeatUnavailableException extends DomainException {

  public SeatUnavailableException(List<String> seatLabels) {
    super(ErrorCode.SEAT_UNAVAILABLE, "Seats no longer available: " + String.join(",", seatLabels), seatLabels);
  }

  public SeatUnavailableException(List<String> seatLabels, Throwable cause) {
    super(ErrorCode.SEAT_UNAVAILABLE, "Seats no longer available: " + String.join(",", seatLabels), seatLabels, cause);
  }
}
