package com.airline.booking.schedule.dto;

import com.airline.booking.schedule.FlightSchedule;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

public record ScheduleResponse(Long id,
                               String flightNumber,
                               String origin,
                               String destination,
                               LocalTime departureTime,
                               LocalTime arrivalTime,
                               short arrivalDayOffset,
                               String aircraftCode,
                               String aircraftType,
                               int totalSeats,
                               Set<DayOfWeek> daysOfOperation,
                               LocalDate validFrom,
                               LocalDate validTo,
                               boolean active) {

    /**
     * Must be called inside the service's transaction: walks lazy associations.
     *
     * <p>daysOfOperation is exposed as a day set, never the raw mask. The mask is a
     * storage detail; returning 21 would force every client to reimplement the bit
     * mapping.
     */
    public static ScheduleResponse from(FlightSchedule s) {
        return new ScheduleResponse(
                s.getId(),
                s.getFlightNumber(),
                s.getSourceAirport().getIataCode(),
                s.getDestinationAirport().getIataCode(),
                s.getDepartureTime(),
                s.getArrivalTime(),
                s.getArrivalDayOffset(),
                s.getAircraft().getAircraftCode(),
                s.getAircraft().getAircraftType(),
                s.getAircraft().capacity(),
                s.getDaysOfOperation().days(),
                s.getValidFrom(),
                s.getValidTo(),
                s.isActive());
    }
}
