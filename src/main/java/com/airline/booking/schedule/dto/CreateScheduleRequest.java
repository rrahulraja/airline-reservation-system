package com.airline.booking.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Admin request to create a schedule.
 *
 * <p>Bean validation covers SHAPE only: presence, pattern, range. Cross-field rules
 * live in {@link com.airline.booking.schedule.ScheduleValidator}, because an
 * annotation cannot see two fields at once without a class-level validator that would
 * be harder to read than the explicit check.
 */
public record CreateScheduleRequest(

        @NotBlank
        @Pattern(regexp = "^[A-Z]{2}\\d{1,4}$",
                 message = "must be two uppercase letters followed by 1-4 digits, e.g. XY101")
        String flightNumber,

        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter IATA code")
        String origin,

        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter IATA code")
        String destination,

        @NotNull LocalTime departureTime,

        @NotNull LocalTime arrivalTime,

        @Min(0) @Max(1) short arrivalDayOffset,

        @NotBlank String aircraftCode,

        @NotEmpty(message = "a schedule must operate on at least one day")
        Set<DayOfWeek> daysOfOperation,

        @NotNull LocalDate validFrom,

        @NotNull LocalDate validTo) {
}
