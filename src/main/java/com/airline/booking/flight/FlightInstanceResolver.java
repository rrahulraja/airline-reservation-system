package com.airline.booking.flight;

import com.airline.booking.schedule.FlightSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * THE SEAM between the flight module and the booking module.
 *
 * <p>Booking code materializes instances through this one method and nothing else. If the
 * generation strategy ever changes from lazy to eager, this class changes and nothing in
 * the booking package does.
 */
@Service
public class FlightInstanceResolver {

    private final FlightInstanceRepository repository;

    public FlightInstanceResolver(FlightInstanceRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns the instance for (schedule, date), creating it if absent.
     *
     * <p>MANDATORY propagation: callers must already have a transaction open, because a
     * booking's instance creation and its seat claim have to succeed or fail together.
     *
     * <p>INSERT first, then SELECT, never the other way round. Select-then-insert has a
     * race window between the check and the write:
     * <pre>
     *   Tx A: SELECT -> nothing      Tx B: SELECT -> nothing
     *   Tx A: INSERT -> ok           Tx B: INSERT -> UNIQUE VIOLATION
     * </pre>
     * Insert-first has none: the loser blocks on the index tuple, then DO NOTHING makes
     * its insert a no-op and its SELECT finds the winner's committed row.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public FlightInstance resolveOrCreate(FlightSchedule schedule, LocalDate date) {
        Instant departureAt = date.atTime(schedule.getDepartureTime()).toInstant(ZoneOffset.UTC);
        Instant arrivalAt = date.plusDays(schedule.getArrivalDayOffset())
                                .atTime(schedule.getArrivalTime())
                                .toInstant(ZoneOffset.UTC);

        repository.upsert(schedule.getId(), date, departureAt, arrivalAt);

        return repository.findByScheduleIdAndFlightDate(schedule.getId(), date)
                .orElseThrow(() -> new IllegalStateException(
                        "flight_instance missing immediately after a successful upsert for schedule "
                        + schedule.getId() + " on " + date
                        + " - check that upsert joined this transaction"));
    }
}
