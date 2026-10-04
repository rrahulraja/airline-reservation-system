package com.airline.booking.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SeatAssignmentRepository extends JpaRepository<SeatAssignment, Long> {

    List<SeatAssignment> findByBookingId(long bookingId);

    /**
     * Deletes lapsed holds for one flight, so a booking never fails because of a hold the
     * sweep job has not reached yet. This is what makes the sweep cleanup rather than a
     * correctness dependency.
     *
     * <p>A native delete rather than load-then-delete: there is nothing to load, and one
     * statement replaces N selects plus N deletes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            DELETE FROM seat_assignment
            WHERE flight_instance_id = :instanceId
              AND status = 'HELD'
              AND expires_at IS NOT NULL
              AND expires_at <= :now
            """, nativeQuery = true)
    int purgeLapsedHolds(@Param("instanceId") long instanceId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SeatAssignment sa WHERE sa.booking.id = :bookingId")
    int deleteByBookingId(@Param("bookingId") long bookingId);

    /**
     * Releases the inventory of several bookings in one statement, for the expiry sweep.
     *
     * <p>One statement rather than a loop matters here beyond efficiency: every call
     * carries clearAutomatically, which detaches the whole persistence context, so a loop
     * would detach the Booking entities the sweep still needs to update.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM SeatAssignment sa WHERE sa.booking.id IN :bookingIds")
    int deleteByBookingIdIn(@Param("bookingIds") java.util.Collection<Long> bookingIds);
}
