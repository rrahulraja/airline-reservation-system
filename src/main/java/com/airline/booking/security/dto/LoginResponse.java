package com.airline.booking.security.dto;

import com.airline.booking.security.Role;
import java.time.Instant;

public record LoginResponse(String token, Role role, Instant expiresAt) {

}
