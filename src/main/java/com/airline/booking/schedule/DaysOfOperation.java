package com.airline.booking.schedule;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which weekdays a schedule operates on, stored as a 7-bit mask.
 *
 * <p>Bit 0 is Monday, bit 6 is Sunday, matching {@code DayOfWeek.getValue() - 1} so
 * no lookup table is needed. One SMALLINT column, no child table, and a search
 * predicate that is a single bitwise AND.
 *
 * <p>A record because it is an immutable value: two instances with the same mask are
 * the same thing.
 */
public record DaysOfOperation(short mask) {

    private static final short ALL_DAYS = 0b1111111;   // 127

    public DaysOfOperation {
        if (mask < 1 || mask > ALL_DAYS) {
            throw new IllegalArgumentException(
                    "days_of_operation must be between 1 and 127, got " + mask);
        }
    }

    public static DaysOfOperation ofMask(short mask) {
        return new DaysOfOperation(mask);
    }

    public static DaysOfOperation of(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("A schedule must operate on at least one day");
        }
        short mask = 0;
        for (DayOfWeek day : days) {
            mask |= bitFor(day);
        }
        return new DaysOfOperation(mask);
    }

    /**
     * The single bit for one weekday. Monday = 1, Tuesday = 2, Wednesday = 4,
     * Thursday = 8, Friday = 16, Saturday = 32, Sunday = 64.
     *
     * <p>The native flight-search query builds its {@code :dayBit} parameter with this
     * method. If this mapping ever changes, that query changes with it, which is why
     * there is only one definition.
     */
    public static short bitFor(DayOfWeek day) {
        return (short) (1 << (day.getValue() - 1));
    }

    public boolean operatesOn(DayOfWeek day) {
        return (mask & bitFor(day)) != 0;
    }

    public Set<DayOfWeek> days() {
        EnumSet<DayOfWeek> result = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (operatesOn(day)) {
                result.add(day);
            }
        }
        return result;
    }
}
