package com.airline.booking.support;

import com.airline.booking.security.AppUserRepository;
import com.airline.booking.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Mints real tokens for the seeded users, for use in integration tests.
 *
 * <p>Goes through {@link JwtService} rather than performing an HTTP login, so a test
 * of flight search does not fail because something unrelated broke in
 * AuthController. AuthFlowTest covers the HTTP login path itself.
 */
@Component
public class TokenFactory {

  private final AppUserRepository users;
  private final JwtService jwtService;

  @Autowired
  public TokenFactory(AppUserRepository users, JwtService jwtService) {
    this.users = users;
    this.jwtService = jwtService;
  }

  public String adminToken() {
    return bearer("admin");
  }

  public String customerToken() {
    return bearer("customer");
  }

  public Long customerId() {
    return users.findByUsername("customer").orElseThrow().getId();
  }

  private String bearer(String username) {
    var user = users.findByUsername(username)
        .orElseThrow(() -> new IllegalStateException(
            "Seed user '" + username + "' missing — check V2__seed_reference.sql"));
    return "Bearer " + jwtService.mint(user);
  }


}
