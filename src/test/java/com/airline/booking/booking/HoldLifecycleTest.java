package com.airline.booking.booking;

import com.airline.booking.booking.dto.CreateBookingRequest;
import com.airline.booking.booking.dto.CreateHoldRequest;
import com.airline.booking.booking.dto.PassengerRequest;
import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.InventoryFixture;
import com.airline.booking.support.MutableClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the hold lifecycle with a clock the test controls, so nothing sleeps.
 *
 * <p>The sweep interval is pushed past the suite's lifetime so the scheduled job never
 * fires on its own: every sweep here is an explicit call, which keeps failures
 * reproducible.
 */
@Import(HoldLifecycleTest.MutableClockConfig.class)
@TestPropertySource(properties = "booking.hold.sweep-interval=PT24H")
class HoldLifecycleTest extends AbstractIntegrationTest {

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private HoldExpiryJob expiryJob;

    @Autowired
    private InventoryFixture inventory;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetClock() {
        clock.setToSystemTime();
    }

    @AfterEach
    void tidy() {
        inventory.cleanUp();
    }

    @Test
    @DisplayName("a hold creates a HELD booking with HELD seats and an expiry")
    void holdCreatesHeldBooking() {
        Instant before = clock.instant();

        var response = holdService.hold(holdRequest(wednesday(), "5A"), null);

        assertThat(response.status()).isEqualTo("HELD");
        assertThat(response.holdExpiresAt()).isEqualTo(before.plus(Duration.ofMinutes(5)));
        assertThat(response.seats()).containsExactly("5A");
        assertThat(seatStatus(response.pnr())).containsExactly("HELD");
    }

    @Test
    @DisplayName("a held seat blocks a direct booking")
    void heldSeatBlocksBooking() {
        LocalDate date = wednesday();
        holdService.hold(holdRequest(date, "6A"), null);

        assertThatThrownBy(() -> bookingService.create(bookingRequest(date, "6A"), null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.SEAT_UNAVAILABLE);
    }

    @Test
    @DisplayName("confirming flips the SAME seat rows to BOOKED, never releasing the seat")
    void confirmFlipsInPlace() {
        var held = holdService.hold(holdRequest(wednesday(), "7A"), null);
        List<Long> rowIdsBefore = seatRowIds(held.pnr());

        var confirmed = holdService.confirm(held.pnr());

        assertThat(confirmed.status()).isEqualTo("CONFIRMED");
        assertThat(confirmed.holdExpiresAt()).isNull();
        assertThat(seatStatus(held.pnr())).containsExactly("BOOKED");
        assertThat(seatExpiryIsNull(held.pnr())).isTrue();

        // THE assertion for this step: the same rows were updated, not deleted and
        // reinserted. Delete-then-insert would open a window for a competing booking.
        assertThat(seatRowIds(held.pnr()))
                .as("confirm must UPDATE the existing seat rows in place")
                .containsExactlyElementsOf(rowIdsBefore);
    }

    @Test
    @DisplayName("confirming after the hold lapses is 409 HOLD_EXPIRED")
    void confirmAfterExpiry() {
        var held = holdService.hold(holdRequest(wednesday(), "8A"), null);

        clock.advance(Duration.ofMinutes(6));

        assertThatThrownBy(() -> holdService.confirm(held.pnr()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.HOLD_EXPIRED);
    }

    @Test
    @DisplayName("confirming an already-confirmed booking is 409 BOOKING_NOT_CONFIRMABLE")
    void confirmAlreadyConfirmed() {
        var held = holdService.hold(holdRequest(wednesday(), "9A"), null);
        holdService.confirm(held.pnr());

        assertThatThrownBy(() -> holdService.confirm(held.pnr()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.BOOKING_NOT_CONFIRMABLE);
    }

    @Test
    @DisplayName("confirming a cancelled booking is 409")
    void confirmCancelled() {
        var held = holdService.hold(holdRequest(wednesday(), "10A"), null);
        bookingService.cancel(held.pnr());

        assertThatThrownBy(() -> holdService.confirm(held.pnr()))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.BOOKING_NOT_CONFIRMABLE);
    }

    @Test
    @DisplayName("a lapsed hold frees the seat for booking BEFORE any sweep runs")
    void lapsedHoldIsBookableBeforeSweep() {
        LocalDate date = wednesday();
        var held = holdService.hold(holdRequest(date, "11A"), null);

        clock.advance(Duration.ofMinutes(6));

        // No sweep call. This proves purgeLapsedHolds in the booking path, and is why the
        // job is cleanup rather than a correctness dependency.
        var booked = bookingService.create(bookingRequest(date, "11A"), null);

        assertThat(booked.status()).isEqualTo("CONFIRMED");
        assertThat(bookingStatus(held.pnr())).as("not yet swept").isEqualTo("HELD");
    }

    @Test
    @DisplayName("the sweep marks lapsed holds EXPIRED and deletes their seats")
    void sweepExpiresAndDeletes() {
        var held = holdService.hold(holdRequest(wednesday(), "12A"), null);

        clock.advance(Duration.ofMinutes(6));

        assertThat(expiryJob.sweep()).isEqualTo(1);
        assertThat(bookingStatus(held.pnr())).isEqualTo("EXPIRED");
        assertThat(seatRowIds(held.pnr())).isEmpty();
    }

    @Test
    @DisplayName("the sweep is idempotent")
    void sweepIsIdempotent() {
        holdService.hold(holdRequest(wednesday(), "13A"), null);
        clock.advance(Duration.ofMinutes(6));

        assertThat(expiryJob.sweep()).isEqualTo(1);
        assertThat(expiryJob.sweep()).as("a second sweep has nothing to do").isZero();
    }

    @Test
    @DisplayName("the sweep leaves active holds alone")
    void sweepLeavesActiveHoldsAlone() {
        LocalDate date = wednesday();
        var lapsing = holdService.hold(holdRequest(date, "14A"), null);

        clock.advance(Duration.ofMinutes(6));
        var fresh = holdService.hold(holdRequest(date, "14B"), null);

        assertThat(expiryJob.sweep()).isEqualTo(1);
        assertThat(bookingStatus(lapsing.pnr())).isEqualTo("EXPIRED");
        assertThat(bookingStatus(fresh.pnr())).isEqualTo("HELD");
        assertThat(seatRowIds(fresh.pnr())).hasSize(1);
    }

    @Test
    @DisplayName("a held booking can be cancelled, releasing its seats")
    void cancelHeldBooking() {
        var held = holdService.hold(holdRequest(wednesday(), "15A"), null);

        var cancelled = bookingService.cancel(held.pnr());

        // One lifecycle for holds and bookings: cancel works on both.
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(seatRowIds(held.pnr())).isEmpty();
    }

    @Test
    @DisplayName("a flight whose departure has passed cannot be booked, deterministically")
    void alreadyDepartedFlightIsRejected() {
        // XY201 operates daily at 06:00 UTC. Placing "now" at 14:00 on a date inside the
        // window makes today's departure unambiguously past, with no dependence on when
        // the suite happens to run.
        LocalDate today = LocalDate.now().plusDays(3);
        clock.set(today.atTime(14, 0).toInstant(ZoneOffset.UTC));

        assertThatThrownBy(() -> bookingService.create(
                new CreateBookingRequest("XY201", today, "Late",
                        List.of(new PassengerRequest("Late", "1A"))), null))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).code())
                .isEqualTo(ErrorCode.FLIGHT_NOT_OPERATING);
    }

    // ---------------------------------------------------------------- fixtures

    @TestConfiguration
    static class MutableClockConfig {

        /**
         * ONE bean, not two. MutableClock IS-A Clock, so @Primary here makes every Clock
         * injection point resolve to it while @Autowired MutableClock gets the same
         * instance. Two @Primary beans of type Clock would fail with "more than one
         * primary bean found", and naming a bean `clock` would collide with ClockConfig's,
         * since Boot disables bean-definition overriding by default.
         */
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.now());
        }
    }

    // ------------------------------------------------------------------ helpers

    private static CreateHoldRequest holdRequest(LocalDate date, String seat) {
        return new CreateHoldRequest("XY101", date, "Hold Test",
                List.of(new PassengerRequest("Passenger " + seat, seat)));
    }

    private static CreateBookingRequest bookingRequest(LocalDate date, String seat) {
        return new CreateBookingRequest("XY101", date, "Booking Test",
                List.of(new PassengerRequest("Passenger " + seat, seat)));
    }

    private String bookingStatus(String pnr) {
        return jdbc.queryForObject("SELECT status FROM booking WHERE pnr = ?", String.class, pnr);
    }

    private List<String> seatStatus(String pnr) {
        return jdbc.queryForList("""
                SELECT sa.status FROM seat_assignment sa
                JOIN booking b ON b.id = sa.booking_id WHERE b.pnr = ?
                """, String.class, pnr);
    }

    private List<Long> seatRowIds(String pnr) {
        return jdbc.queryForList("""
                SELECT sa.id FROM seat_assignment sa
                JOIN booking b ON b.id = sa.booking_id WHERE b.pnr = ? ORDER BY sa.id
                """, Long.class, pnr);
    }

    private boolean seatExpiryIsNull(String pnr) {
        Integer nonNull = jdbc.queryForObject("""
                SELECT count(*) FROM seat_assignment sa
                JOIN booking b ON b.id = sa.booking_id
                WHERE b.pnr = ? AND sa.expires_at IS NOT NULL
                """, Integer.class, pnr);
        return nonNull != null && nonNull == 0;
    }

    private static LocalDate wednesday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
    }
}
