package com.airline.booking.security;


import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import com.airline.booking.security.dto.LoginRequest;
import com.airline.booking.security.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthController(AppUserRepository users,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  @Transactional(readOnly = true)
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    AppUser user = users.findByUsername(request.username())
        .orElseThrow(AuthController::invalidCredentials);

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw invalidCredentials();
    }

    String token = jwtService.mint(user);
    return new LoginResponse(token, user.getRole(), jwtService.expiryOf(jwtService.now()));
  }


  /**
   * One exception for both "no such user" and "wrong password", with identical
   * message. Distinguishing them tells an attacker which usernames exist.
   */
  private static DomainException invalidCredentials() {
    return new DomainException(ErrorCode.UNAUTHENTICATED, "Invalid username or password");
  }

}
