package com.airline.booking.booking;

import com.airline.booking.config.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Expires lapsed holds and deletes their seat inventory.
 *
 * <p>CLEANUP ONLY. Correctness does not depend on this job running: the search query and
 * the seat map both filter on expires_at > now(), and the booking path purges lapsed holds
 * inside its own transaction. A seat is rebookable the instant its hold lapses, whether or
 * not the job has ever run. This job exists to keep the table small and booking states
 * honest.
 *
 * <p>Known limitation: on a multi-instance deployment every node runs it. The fix is
 * ShedLock or a PostgreSQL advisory lock; declared out of scope and documented in the
 * README.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingRepository bookingRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final BookingProperties properties;
    private final Clock clock;

    public HoldExpiryJob(BookingRepository bookingRepository,
                         SeatAssignmentRepository seatAssignmentRepository,
                         BookingProperties properties,
                         Clock clock) {
        this.bookingRepository = bookingRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${booking.hold.sweep-interval}")
    public void scheduledSweep() {
        int expired = sweep();
        if (expired > 0) {
            log.info("Hold expiry sweep expired {} booking(s)", expired);
        }
    }

    /**
     * Expires one batch of lapsed holds.
     *
     * <p>Separate from the scheduled entry point and returning a count, which is how tests
     * drive it deterministically instead of waiting for the scheduler.
     *
     * <p>Idempotent: a second call with nothing lapsed returns 0 and changes nothing.
     */
    @Transactional
    public int sweep() {
        Instant now = clock.instant();

        List<Booking> lapsed = bookingRepository.findLapsedHolds(
                now, PageRequest.of(0, properties.holdSweepBatchSize()));

        if (lapsed.isEmpty()) {
            return 0;
        }

        List<Long> bookingIds = lapsed.stream().map(Booking::getId).toList();

        // ORDER MATTERS. Mark the bookings EXPIRED and flush that first, because the
        // bulk delete below carries clearAutomatically, which detaches the entire
        // persistence context. Deleting first would detach these Booking instances and
        // every subsequent expire() would be a no-op on a detached object: the seats
        // would be released while the bookings stayed HELD forever.
        lapsed.forEach(booking -> booking.expire(now));
        bookingRepository.flush();

        // One statement, not a loop: each call clears the context, so a loop would
        // detach the remaining entities mid-iteration.
        seatAssignmentRepository.deleteByBookingIdIn(bookingIds);

        return lapsed.size();
    }
}
