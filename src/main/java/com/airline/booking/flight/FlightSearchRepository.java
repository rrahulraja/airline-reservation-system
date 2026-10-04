package com.airline.booking.flight;

import com.airline.booking.schedule.FlightSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * The only hand-written SQL in the codebase, isolated here so the deliberate exception
 * to "JPA everywhere" is obvious to a reviewer rather than looking like drift.
 *
 * <p>Typed on FlightSchedule because a Spring Data repository needs a managed entity
 * for its generic parameter, and FlightSchedule is the table the query drives from.
 */
public interface FlightSearchRepository extends JpaRepository<FlightSchedule, Long> {

    /**
     * Flights operating between two airports on one date, with live availability.
     *
     * <p>Native SQL on purpose: JPQL cannot express the bitwise AND against
     * days_of_operation, nor LEFT JOIN LATERAL.
     *
     * <p>The LEFT JOIN to flight_instance must stay a LEFT JOIN. Most flights have no
     * instance row because nobody has booked them yet, and an inner join would hide
     * exactly those. fi.id is NULL in that case, so the lateral count matches nothing
     * and COALESCE yields 0.
     *
     * <p>The expires_at predicate in the lateral count is load-bearing: without it a
     * lapsed hold keeps counting as occupied until the sweep job runs, which would make
     * that job a correctness dependency rather than a cleanup task.
     */
    @Query(value = """
            SELECT s.id                 AS scheduleId,
                   s.flight_number      AS flightNumber,
                   s.departure_time     AS departureTime,
                   s.arrival_time       AS arrivalTime,
                   s.arrival_day_offset AS arrivalDayOffset,
                   a.aircraft_type      AS aircraftType,
                   a.row_count          AS rowCount,
                   a.seat_letters       AS seatLetters,
                   COALESCE(c.occupied, 0) AS occupied
            FROM flight_schedule s
            JOIN aircraft a ON a.id = s.aircraft_id
            LEFT JOIN flight_instance fi
                   ON fi.schedule_id = s.id AND fi.flight_date = :date
            LEFT JOIN LATERAL (
                SELECT count(*) AS occupied
                FROM seat_assignment sa
                WHERE sa.flight_instance_id = fi.id
                  AND sa.status IN ('HELD', 'BOOKED')
                  AND (sa.expires_at IS NULL OR sa.expires_at > now())
            ) c ON TRUE
            WHERE s.active
              AND s.source_airport_id = :sourceAirportId
              AND s.destination_airport_id = :destinationAirportId
              AND :date BETWEEN s.valid_from AND s.valid_to
              AND (s.days_of_operation & :dayBit) > 0
            ORDER BY s.departure_time
            """, nativeQuery = true)
    List<FlightSearchRow> search(@Param("sourceAirportId") long sourceAirportId,
                                 @Param("destinationAirportId") long destinationAirportId,
                                 @Param("date") LocalDate date,
                                 @Param("dayBit") short dayBit);
}
