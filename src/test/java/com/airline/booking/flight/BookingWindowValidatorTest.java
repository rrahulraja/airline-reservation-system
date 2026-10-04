package com.airline.booking.flight;

import com.airline.booking.common.exceptions.DateOutOfBookingWindowException;
import com.airline.booking.config.BookingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Review Focus item 4: an off-by-one here is invisible in manual testing, so all four
 * boundaries are asserted explicitly.
 */
class BookingWindowValidatorTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 2);

    private final BookingWindowValidator validator = new BookingWindowValidator(
            Clock.fixed(NOW, ZoneOffset.UTC),
            new BookingProperties(365, 9,
                    new BookingProperties.Hold(Duration.ofMinutes(5), Duration.ofMinutes(1), 200)));

    @Test
    @DisplayName("today is bookable")
    void todayAccepted() {
        assertThatCode(() -> validator.validate(TODAY)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the last day of the window is bookable")
    void lastDayOfWindowAccepted() {
        assertThatCode(() -> validator.validate(TODAY.plusDays(365))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("one day past the window is rejected")
    void oneDayPastWindowRejected() {
        assertThatThrownBy(() -> validator.validate(TODAY.plusDays(366)))
                .isInstanceOf(DateOutOfBookingWindowException.class);
    }

    @Test
    @DisplayName("yesterday is rejected")
    void yesterdayRejected() {
        assertThatThrownBy(() -> validator.validate(TODAY.minusDays(1)))
                .isInstanceOf(DateOutOfBookingWindowException.class);
    }

    @Test
    @DisplayName("the window is 365 days, not one calendar year")
    void windowIsDaysNotYears() {
        assertThatCode(() -> validator.validate(LocalDate.of(2027, 10, 2)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(LocalDate.of(2027, 10, 3)))
                .isInstanceOf(DateOutOfBookingWindowException.class);
    }
}
