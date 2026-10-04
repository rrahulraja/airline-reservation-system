package com.airline.booking.booking;

/**
 * Status of a seat in live inventory.
 *
 * <p>Only two values, and ux_seat_active covers both. There is no RELEASED state:
 * releasing a seat means DELETING the row, which is what lets the partial index free the
 * seat with no extra bookkeeping.
 */
public enum SeatStatus {
    HELD,
    BOOKED
}
