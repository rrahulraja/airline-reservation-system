package com.airline.booking.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single source of "now" for the whole application.
 *
 * <p>Two things here are time-dependent and both are easy to get subtly wrong: the
 * 365-day booking window, and seat-hold expiry. Injecting a {@link Clock} lets
 * tests pin or advance time instead of calling {@code Thread.sleep} — the
 * difference between a hold-expiry test that runs in milliseconds and one that
 * takes five minutes and still flakes.
 *
 * <p>UTC, matching the storage convention.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

}
