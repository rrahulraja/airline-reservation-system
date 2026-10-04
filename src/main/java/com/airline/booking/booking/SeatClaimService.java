package com.airline.booking.booking;

import com.airline.booking.common.exceptions.SeatUnavailableException;
import com.airline.booking.flight.FlightInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * THE seat claim primitive. All booking entry points go through this one method, so a
 * single concurrency proof covers all of them.
 *
 * <p>No row locking. ux_seat_active decides the winner, which is a stronger guarantee than
 * a lock: it holds across application bugs and across multiple application instances, and
 * losers fail fast instead of queueing.
 */
@Service
public class SeatClaimService {

    private static final Logger log = LoggerFactory.getLogger(SeatClaimService.class);
    private static final String SEAT_UNIQUE_INDEX = "ux_seat_active";
    /** ANSI SQL state for a unique constraint violation. */
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    @PersistenceContext
    private EntityManager entityManager;

    private final SeatAssignmentRepository repository;

    public SeatClaimService(SeatAssignmentRepository repository) {
        this.repository = repository;
    }

    /**
     * Claims every seat, or none.
     *
     * @param sortedLabels seat labels, already normalized AND SORTED. Sorting gives every
     *                     transaction the same acquisition order on the index, which
     *                     removes the deadlock case between requests whose seat sets
     *                     overlap in different orders:
     *                     <pre>
     *                     Tx A: insert 1A (holds tuple)   Tx B: insert 1B (holds tuple)
     *                     Tx A: insert 1B -> waits on B   Tx B: insert 1A -> waits on A
     *                                                     -> deadlock, SQLSTATE 40P01
     *                     </pre>
     * @throws SeatUnavailableException when any seat is already taken (409)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<SeatAssignment> claimSeats(FlightInstance instance,
                                          Booking booking,
                                          List<String> sortedLabels,
                                          SeatStatus status,
                                          Instant expiresAt) {

        List<SeatAssignment> assignments = new ArrayList<>(sortedLabels.size());
        for (String label : sortedLabels) {
            assignments.add(SeatAssignment.create(instance, booking, label, status, expiresAt));
        }

        try {
            repository.saveAll(assignments);

            // THE CRITICAL LINE.
            //
            // Hibernate flushes at transaction commit by default, and commit happens
            // OUTSIDE this method, so without an explicit flush the constraint violation
            // escapes this catch entirely and surfaces as a TransactionSystemException
            // from the commit, which the global handler renders as a 500 instead of a 409.
            //
            // Flushing forces the INSERTs to reach PostgreSQL while the exception is still
            // catchable here.
            entityManager.flush();

            return assignments;

        } catch (DataIntegrityViolationException e) {
            if (isSeatUniqueViolation(e)) {
                log.warn("Seat claim lost the race for instance {} seats {}",
                         instance.getId(), sortedLabels);
                // Cause retained so the PostgreSQL detail stays in the logs.
                throw new SeatUnavailableException(sortedLabels, e);
            }
            // Some other constraint failed. Do NOT swallow it into a misleading 409.
            throw e;
        }
    }

    /**
     * Whether this violation came from the seat uniqueness index specifically.
     *
     * <p>Catching every DataIntegrityViolationException as SEAT_UNAVAILABLE would report
     * "seat taken" when the real failure was, say, a null booking_id. Only this index may
     * produce a 409; every other violation stays loud.
     *
     * <p>Identified by the SQL state rather than by a driver class, so nothing here depends
     * on the PostgreSQL driver at compile time. 23505 is the ANSI unique-violation state.
     * The constraint name is matched from the message because JDBC exposes no portable
     * accessor for it, and it is the INDEX name, which is why ux_seat_active had to be
     * created as a named index.
     */
    private boolean isSeatUniqueViolation(DataIntegrityViolationException e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof SQLException sql
                && UNIQUE_VIOLATION_SQL_STATE.equals(sql.getSQLState())) {
                return mentionsSeatIndex(sql.getMessage());
            }
            cause = cause.getCause();
        }
        return mentionsSeatIndex(e.getMostSpecificCause().getMessage());
    }

    private static boolean mentionsSeatIndex(String message) {
        return message != null && message.contains(SEAT_UNIQUE_INDEX);
    }
}
