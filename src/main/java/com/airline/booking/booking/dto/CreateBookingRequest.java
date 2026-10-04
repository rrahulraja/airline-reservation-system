package com.airline.booking.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * @param passengers @Valid on the list is what makes the nested @NotBlank on each
 *                   PassengerRequest actually run. Without it a passenger with a blank
 *                   name passes validation silently.
 */
public record CreateBookingRequest(

        @NotBlank String flightNumber,

        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate flightDate,

        String contactName,

        @NotEmpty @Valid List<PassengerRequest> passengers) {
}
