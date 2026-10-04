package com.airline.booking.booking;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @EntityGraph(attributePaths = {"passengers", "flightInstance", "flightInstance.schedule"})
    Optional<Booking> findByPnr(String pnr);

    boolean existsByPnr(String pnr);

    /**
     * Held bookings whose hold has lapsed, oldest first.
     *
     * <p>Backed by the partial index ix_booking_hold_expiry, so this scans only held rows
     * rather than the whole table.
     */
    @Query("""
            SELECT b FROM Booking b
            WHERE b.status = com.airline.booking.booking.BookingStatus.HELD
              AND b.holdExpiresAt <= :now
            ORDER BY b.holdExpiresAt ASC
            """)
    List<Booking> findLapsedHolds(@Param("now") Instant now, Pageable pageable);
}
