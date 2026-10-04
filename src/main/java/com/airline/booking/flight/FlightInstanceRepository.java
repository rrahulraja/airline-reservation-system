package com.airline.booking.flight;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface FlightInstanceRepository extends JpaRepository<FlightInstance, Long> {

    Optional<FlightInstance> findByScheduleIdAndFlightDate(long scheduleId, LocalDate flightDate);

    /**
     * Race-safe lazy creation.
     *
     * <p>ON CONFLICT DO NOTHING against the unique constraint on
     * (schedule_id, flight_date) means two concurrent first-bookers converge on one row
     * with no application lock: the loser blocks on the index until the winner commits,
     * then its own insert becomes a no-op.
     *
     * <p>clearAutomatically is required, or the subsequent findBy can be served from a
     * stale first-level cache and return empty though the row exists.
     *
     * <p>MANDATORY propagation is required. This must join the caller's transaction: with
     * its own, the instance would commit independently of the booking and a rolled-back
     * booking would leave an orphan row.
     *
     * @return 1 when it inserted, 0 when another transaction got there first. Both are
     *         success.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            INSERT INTO flight_instance (schedule_id, flight_date, departure_at, arrival_at, status)
            VALUES (:scheduleId, :flightDate, :departureAt, :arrivalAt, 'SCHEDULED')
            ON CONFLICT (schedule_id, flight_date) DO NOTHING
            """, nativeQuery = true)
    int upsert(@Param("scheduleId") long scheduleId,
               @Param("flightDate") LocalDate flightDate,
               @Param("departureAt") Instant departureAt,
               @Param("arrivalAt") Instant arrivalAt);
}
