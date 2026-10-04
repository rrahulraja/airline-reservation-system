package com.airline.booking.booking;

import com.airline.booking.schedule.FlightSchedule;

import java.util.List;

/**
 * A booking request after validation: the resolved schedule, the normalized and sorted
 * seat labels, and the passengers paired to them.
 *
 * <p>Exists so booking and hold creation share one validation result without sharing one
 * method body.
 */
public record ResolvedBookingRequest(FlightSchedule schedule,
                                     List<String> sortedSeatLabels,
                                     List<PassengerAssignment> passengers) {

    public record PassengerAssignment(String fullName, String seatLabel) {
    }
}
