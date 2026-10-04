package com.airline.booking.flight;

import java.time.LocalTime;

/**
 * Spring Data interface projection for the native search query.
 *
 * <p>Not an entity: the row carries columns from three tables plus a computed
 * occupancy count, which no single entity models.
 *
 * <p>An interface rather than a record: projections bind by accessor name against the
 * query's column aliases and give a clear error when an alias is missing, whereas
 * record projection depends on parameter names surviving compilation.
 */
public interface FlightSearchRow {

    Long getScheduleId();

    String getFlightNumber();

    LocalTime getDepartureTime();

    LocalTime getArrivalTime();

    Short getArrivalDayOffset();

    String getAircraftType();

    Integer getRowCount();

    String getSeatLetters();

    /** Active, non-lapsed seat assignments on this flight, 0 if none. */
    Integer getOccupied();
}
