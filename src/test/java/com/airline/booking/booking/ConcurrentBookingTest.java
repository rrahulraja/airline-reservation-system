package com.airline.booking.booking;

import com.airline.booking.booking.dto.CreateBookingRequest;
import com.airline.booking.booking.dto.PassengerRequest;
import com.airline.booking.common.exceptions.SeatUnavailableException;
import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.ConcurrentRunner;
import com.airline.booking.support.InventoryFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The suite the whole design exists to pass.
 *
 * <p>NOT annotated @Transactional, deliberately: each worker must get its own transaction,
 * or there is no contention to test.
 */
class ConcurrentBookingTest extends AbstractIntegrationTest {

    private static final int THREADS = 20;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private InventoryFixture inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tidy() {
        inventory.cleanUp();
    }

    @Test
    @DisplayName("20 threads claiming one seat: exactly one wins, and one row exists")
    void twentyThreadsSameSeatExactlyOneSucceeds() throws Exception {
        LocalDate date = wednesday();

        var outcome = ConcurrentRunner.runAll(THREADS,
                () -> bookingService.create(request("XY101", date, "15A"), null));

        assertThat(outcome.successCount()).as("exactly one booking may succeed").isEqualTo(1);
        assertThat(outcome.failureCount()).isEqualTo(THREADS - 1);

        // Every loser must fail with a clean 409, not a transaction-system error. A
        // TransactionSystemException here means the explicit flush() in SeatClaimService is
        // missing or outside the try block.
        assertThat(outcome.failures())
                .allSatisfy(t -> assertThat(rootDomainCause(t))
                        .as("loser must fail with SeatUnavailableException, got: %s", t)
                        .isInstanceOf(SeatUnavailableException.class));

        assertThat(seatRowCount(date, "15A"))
                .as("the database must hold exactly one claim for 15A")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("20 threads claiming 20 distinct seats: all succeed, nothing queues")
    void concurrentDistinctSeatsAllSucceed() throws Exception {
        LocalDate date = wednesday();
        var seats = new AtomicInteger(0);

        var outcome = ConcurrentRunner.runAll(THREADS,
                () -> bookingService.create(request("XY101", date, seats.incrementAndGet() + "A"), null));

        // This is the test that rules out SELECT ... FOR UPDATE. A row-lock design passes
        // the same-seat test above but needlessly serializes here, which under load is the
        // difference between a usable and an unusable booking API.
        assertThat(outcome.failures()).as("distinct seats must never conflict").isEmpty();
        assertThat(outcome.successCount()).isEqualTo(THREADS);
        assertThat(totalActiveSeatRows()).isEqualTo(THREADS);
    }

    @Test
    @DisplayName("opposing seat orders do not deadlock, because labels are sorted")
    void opposingSeatOrdersDoNotDeadlock() throws Exception {
        // A fresh date per round, so the previous round's winner does not simply block
        // both workers.
        for (int round = 0; round < 15; round++) {
            LocalDate date = wednesday().plusWeeks(round);

            var outcome = ConcurrentRunner.runPair(
                    () -> bookingService.create(request("XY101", date, "1A", "1B"), null),
                    () -> bookingService.create(request("XY101", date, "1B", "1A"), null));

            assertThat(outcome.failures())
                    .as("round %s must not deadlock", round)
                    .noneMatch(ConcurrentRunner::isDeadlock);

            assertThat(outcome.successCount())
                    .as("round %s must have exactly one winner", round)
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("concurrent first bookings on one flight create exactly one instance row")
    void concurrentFirstBookingCreatesOneInstance() throws Exception {
        LocalDate date = wednesday().plusWeeks(30);
        int before = inventory.countInstances();
        var seats = new AtomicInteger(0);

        var outcome = ConcurrentRunner.runAll(10,
                () -> bookingService.create(request("XY101", date, seats.incrementAndGet() + "C"), null));

        assertThat(outcome.failures()).isEmpty();
        assertThat(outcome.successCount()).isEqualTo(10);

        // Re-proves the ON CONFLICT upsert through the real booking path rather than by
        // calling the resolver directly.
        assertThat(inventory.countInstances())
                .as("ten concurrent first-bookers must share one flight instance")
                .isEqualTo(before + 1);
    }

    // ------------------------------------------------------------------ helpers

    private static CreateBookingRequest request(String flightNumber, LocalDate date, String... seats) {
        var passengers = Arrays.stream(seats)
                .map(seat -> new PassengerRequest("Passenger " + seat, seat))
                .toList();
        return new CreateBookingRequest(flightNumber, date, "Concurrency Test", passengers);
    }

    /** Unwraps proxy and transaction wrappers to find the domain exception. */
    private static Throwable rootDomainCause(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof SeatUnavailableException) {
                return current;
            }
            current = current.getCause();
        }
        return t;
    }

    private int seatRowCount(LocalDate date, String seatLabel) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM seat_assignment sa
                JOIN flight_instance fi ON fi.id = sa.flight_instance_id
                WHERE fi.flight_date = ? AND sa.seat_label = ?
                  AND sa.status IN ('HELD','BOOKED')
                """, Integer.class, date, seatLabel);
    }

    private int totalActiveSeatRows() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM seat_assignment WHERE status IN ('HELD','BOOKED')",
                Integer.class);
    }

    private static LocalDate wednesday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
    }
}
