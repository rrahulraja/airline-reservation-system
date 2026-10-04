package com.airline.booking.security;

import com.airline.booking.common.ApiError;
import com.airline.booking.common.ErrorCode;
import com.airline.booking.common.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 configuration. Note there is no WebSecurityConfigurerAdapter —
 * it was removed. Configuration is a SecurityFilterChain bean built with the
 * lambda DSL, and {@code authorizeRequests} is now {@code authorizeHttpRequests}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public SecurityConfig(JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper, Clock clock) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        // No cookies, no browser form posts, so CSRF protection buys nothing.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth .requestMatchers("/api/auth/login").permitAll()
            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/api/admin/**", "/api/aircraft/**").hasRole(Role.ADMIN.name())
            .anyRequest().authenticated())
        .exceptionHandling(ex -> ex
        .authenticationEntryPoint(authenticationEntryPoint())
        .accessDeniedHandler(accessDeniedHandler()))
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // Cost 10, matching the hashes seeded in V2__seed_reference.sql.
    return new BCryptPasswordEncoder(10);
  }


  /**
   * Renders 401s as ApiError.
   *
   * <p>Required because the filter chain rejects unauthenticated requests before
   * any @RestControllerAdvice runs. Without this, 401 is an HTML error page while
   * every other error in the API is JSON.
   */
  private AuthenticationEntryPoint authenticationEntryPoint() {
    return (request, response, authException) ->
        write(response, HttpServletResponse.SC_UNAUTHORIZED, ErrorCode.UNAUTHENTICATED,
            "Authentication is required to access this resource");
  }

  /** Renders 403s as ApiError, for the same reason. */
  private AccessDeniedHandler accessDeniedHandler() {
    return (request, response, deniedException) ->
        write(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN,
            "You are not permitted to access this resource");
  }

  private void write(HttpServletResponse response, int status, ErrorCode code, String message)
      throws java.io.IOException {
    ApiError error = ApiError.of(code, message, RequestIdFilter.currentOrFallback(), clock);
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getWriter(), error);
  }


}
