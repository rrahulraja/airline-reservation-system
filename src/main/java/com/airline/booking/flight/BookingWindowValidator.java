package com.airline.booking.flight;

import com.airline.booking.common.exceptions.DateOutOfBookingWindowException;
import com.airline.booking.config.BookingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Enforces that a travel date falls inside the bookable window.
 *
 * <p>Its own component because four call sites need the identical rule, and an
 * off-by-one duplicated four times is the most likely silent bug in the project.
 *
 * <p>Both boundaries are inclusive: today is bookable and today + windowDays is
 * bookable.
 */
@Component
public class BookingWindowValidator {

    private final Clock clock;
    private final BookingProperties properties;

    public BookingWindowValidator(Clock clock, BookingProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** Today in UTC, per the storage convention. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * plusDays, not plusYears: a calendar year is 366 days in a leap year, which would
     * make the window silently vary.
     */
    public LocalDate latestBookableDate() {
        return today().plusDays(properties.windowDays());
    }

    public void validate(LocalDate date) {
        LocalDate earliest = today();
        LocalDate latest = latestBookableDate();

        if (date.isBefore(earliest) || date.isAfter(latest)) {
            throw new DateOutOfBookingWindowException(date, earliest, latest);
        }
    }
}
