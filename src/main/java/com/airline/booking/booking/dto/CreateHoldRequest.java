package com.airline.booking.booking.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * Identical in shape to CreateBookingRequest. Kept as a separate type so the two
 * endpoints can diverge later, for instance if a hold took a requested TTL, without a
 * breaking change to either.
 */
public record CreateHoldRequest(

        @NotBlank String flightNumber,

        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate flightDate,

        String contactName,

        @NotEmpty @Valid List<PassengerRequest> passengers) {
}
