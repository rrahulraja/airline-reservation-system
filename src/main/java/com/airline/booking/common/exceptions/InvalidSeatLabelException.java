package com.airline.booking.common.exceptions;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import java.util.List;

public class InvalidSeatLabelException extends DomainException {

  public InvalidSeatLabelException(List<String> labels, String aircraftType) {
    super(ErrorCode.INVALID_SEAT_LABEL,
        "Seat labels not valid for aircraft " + aircraftType + ": "
            + String.join(", ", labels),
        labels);
  }

}
