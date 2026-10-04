package com.airline.booking.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies stateless HS256 tokens.
 *
 * <p>Stateless on purpose: no server-side session store, so the API scales
 * horizontally without shared session state. The trade-off, documented as a
 * limitation, is that a token cannot be revoked before it expires.
 */
@Service
public class JwtService {

  private static final Logger log = LoggerFactory.getLogger(JwtService.class);
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_USER_ID = "uid";

  private final SecretKey key;
  private final Duration expiry;
  private final Clock clock;

  public JwtService(@Value("${security.jwt.secret}") String secret,
      @Value("${security.jwt.expiry}") Duration expiry,
      Clock clock) {
    byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length < 32) {
      // Fail at startup rather than on the first login attempt.
      throw new IllegalStateException(
          "security.jwt.secret must be at least 32 bytes for HS256, got " + keyBytes.length);
    }
    this.key = Keys.hmacShaKeyFor(keyBytes);
    this.expiry = expiry;
    this.clock = clock;
  }

  public String mint(AppUser user) {
    Instant now = clock.instant();
    return Jwts.builder()
        .subject(user.getUsername())
        .claim(CLAIM_ROLE, user.getRole().name())
        .claim(CLAIM_USER_ID, user.getId())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expiry)))
        .signWith(key)
        .compact();
  }

  public Instant expiryOf(Instant issuedAt) {
    return issuedAt.plus(expiry);
  }

  public Instant now() {
    return clock.instant();
  }

  /**
   * Verifies a token and extracts the principal.
   *
   * <p>Returns empty rather than throwing for every failure mode — expired,
   * tampered, malformed, wrong algorithm. The filter treats "no valid token" and
   * "no token" identically, which avoids leaking to an attacker which of the two
   * occurred.
   */
  public Optional<AuthenticatedUser> parse(String token) {
    try {
      var claims = Jwts.parser()
          .verifyWith(key)
          .clock(() -> Date.from(clock.instant()))
          .build()
          .parseSignedClaims(token)
          .getPayload();

      Long userId = claims.get(CLAIM_USER_ID, Long.class);
      String role = claims.get(CLAIM_ROLE, String.class);
      if (userId == null || role == null) {
        return Optional.empty();
      }
      return Optional.of(new AuthenticatedUser(userId, claims.getSubject(), Role.valueOf(role)));

    } catch (ExpiredJwtException e) {
      log.debug("Rejected expired token for subject {}", e.getClaims().getSubject());
      return Optional.empty();
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("Rejected invalid token: {}", e.getMessage());
      return Optional.empty();
    }
  }

}
