package com.airline.booking.booking.dto;

import com.airline.booking.booking.Booking;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BookingResponse(String pnr,
                              String flightNumber,
                              LocalDate flightDate,
                              String status,
                              int passengerCount,
                              List<String> seats,
                              Instant holdExpiresAt,
                              Instant createdAt) {

    /** For a freshly created booking, where the flight details are already in hand. */
    public static BookingResponse from(Booking booking,
                                       List<String> seats,
                                       String flightNumber,
                                       LocalDate flightDate) {
        return new BookingResponse(booking.getPnr(), flightNumber, flightDate,
                                   booking.getStatus().name(), booking.getPassengerCount(),
                                   seats, booking.getHoldExpiresAt(), booking.getCreatedAt());
    }

    /** For a loaded booking. Must be called inside a transaction: walks lazy associations. */
    public static BookingResponse from(Booking booking, List<String> seats) {
        var instance = booking.getFlightInstance();
        return new BookingResponse(booking.getPnr(),
                                   instance.getSchedule().getFlightNumber(),
                                   instance.getFlightDate(),
                                   booking.getStatus().name(),
                                   booking.getPassengerCount(),
                                   seats,
                                   booking.getHoldExpiresAt(),
                                   booking.getCreatedAt());
    }
}
