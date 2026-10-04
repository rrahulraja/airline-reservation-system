package com.airline.booking.booking.dto;

import jakarta.validation.constraints.NotBlank;

public record PassengerRequest(@NotBlank String fullName, @NotBlank String seatLabel) {
}
