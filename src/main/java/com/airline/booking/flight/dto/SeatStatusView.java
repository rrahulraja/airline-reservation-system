package com.airline.booking.flight.dto;

/** A single seat's rendered state. */
public record SeatStatusView(String label, String status) {

    public static final String AVAILABLE = "AVAILABLE";
    public static final String HELD = "HELD";
    public static final String BOOKED = "BOOKED";
}
