package com.airline.booking.flight.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One bookable flight on one date.
 *
 * <p>No scheduleId and no instanceId. The public identity of a flight is
 * flightNumber + flightDate, a direct consequence of lazy instance generation: an
 * instance id may not exist while a customer is looking at the flight.
 */
public record FlightSearchResult(String flightNumber,
                                 String origin,
                                 String destination,
                                 LocalDate flightDate,
                                 Instant departureAt,
                                 Instant arrivalAt,
                                 String aircraftType,
                                 int totalSeats,
                                 int availableSeats) {
}
