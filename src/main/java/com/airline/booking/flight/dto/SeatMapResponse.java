package com.airline.booking.flight.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Grouped by row rather than returned as a flat list, because a client renders a cabin
 * as rows of seats and should not have to parse "12A" to work out where it belongs.
 */
public record SeatMapResponse(String flightNumber,
                              LocalDate flightDate,
                              String aircraftType,
                              int totalSeats,
                              int availableSeats,
                              List<SeatMapRow> rows) {
}
