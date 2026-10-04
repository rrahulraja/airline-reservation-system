package com.airline.booking.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DaysOfOperationTest {

    @Test
    @DisplayName("each weekday maps to its documented bit")
    void bitForAllSevenDays() {
        // Asserted as literals, not a loop. These seven values are the contract the
        // native search query depends on, so they are worth stating explicitly.
        assertThat(DaysOfOperation.bitFor(DayOfWeek.MONDAY)).isEqualTo((short) 1);
        assertThat(DaysOfOperation.bitFor(DayOfWeek.TUESDAY)).isEqualTo((short) 2);
        assertThat(DaysOfOperation.bitFor(DayOfWeek.WEDNESDAY)).isEqualTo((short) 4);
        assertThat(DaysOfOperation.bitFor(DayOfWeek.THURSDAY)).isEqualTo((short) 8);
        assertThat(DaysOfOperation.bitFor(DayOfWeek.FRIDAY)).isEqualTo((short) 16);
        assertThat(DaysOfOperation.bitFor(DayOfWeek.SATURDAY)).isEqualTo((short) 32);
        assertThat(DaysOfOperation.bitFor(DayOfWeek.SUNDAY)).isEqualTo((short) 64);
    }

    @Test
    @DisplayName("every non-empty subset of weekdays round-trips through the mask")
    void roundTripEverySubset() {
        for (short mask = 1; mask <= 127; mask++) {
            Set<DayOfWeek> days = DaysOfOperation.ofMask(mask).days();

            assertThat(DaysOfOperation.of(days).mask())
                    .as("mask %s -> %s -> mask", mask, days)
                    .isEqualTo(mask);
        }
    }

    @Test
    @DisplayName("operatesOn is true only for the days in the set")
    void operatesOn() {
        var monWedFri = DaysOfOperation.of(
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));

        assertThat(monWedFri.mask()).isEqualTo((short) 21);   // 1 + 4 + 16
        assertThat(monWedFri.operatesOn(DayOfWeek.MONDAY)).isTrue();
        assertThat(monWedFri.operatesOn(DayOfWeek.WEDNESDAY)).isTrue();
        assertThat(monWedFri.operatesOn(DayOfWeek.FRIDAY)).isTrue();
        assertThat(monWedFri.operatesOn(DayOfWeek.TUESDAY)).isFalse();
        assertThat(monWedFri.operatesOn(DayOfWeek.THURSDAY)).isFalse();
        assertThat(monWedFri.operatesOn(DayOfWeek.SATURDAY)).isFalse();
        assertThat(monWedFri.operatesOn(DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    @DisplayName("all seven days is mask 127")
    void allDays() {
        assertThat(DaysOfOperation.of(EnumSet.allOf(DayOfWeek.class)).mask())
                .isEqualTo((short) 127);
    }

    @Test
    @DisplayName("an empty day set is rejected")
    void rejectsEmpty() {
        assertThatThrownBy(() -> DaysOfOperation.of(EnumSet.noneOf(DayOfWeek.class)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> DaysOfOperation.of(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a mask outside 1..127 is rejected")
    void rejectsOutOfRangeMask() {
        assertThatThrownBy(() -> DaysOfOperation.ofMask((short) 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DaysOfOperation.ofMask((short) 128))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DaysOfOperation.ofMask((short) -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
