package com.airline.booking.schedule;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScheduleRepository extends JpaRepository<FlightSchedule, Long> {

    /**
     * The single lookup every customer-facing flow uses to resolve a flight number.
     *
     * <p>Fetches the aircraft and both airports eagerly for this query only: every
     * caller needs the aircraft geometry to generate seat labels, so the lazy default
     * would guarantee an N+1 or a LazyInitializationException.
     */
    @EntityGraph(attributePaths = {"aircraft", "sourceAirport", "destinationAirport"})
    Optional<FlightSchedule> findByFlightNumberAndActiveTrue(String flightNumber);

    @EntityGraph(attributePaths = {"aircraft", "sourceAirport", "destinationAirport"})
    Page<FlightSchedule> findAllByActiveTrue(Pageable pageable);

    boolean existsByFlightNumberAndActiveTrue(String flightNumber);
}
