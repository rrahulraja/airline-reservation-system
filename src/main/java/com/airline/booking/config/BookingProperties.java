package com.airline.booking.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Domain policy, externalized. Nothing in a service or entity may hard-code any of
 * these values: a literal in code and a value in {@code application.yml} that
 * disagree is a bug that only appears in whichever environment overrides it.
 *
 * <p>Registered by {@code @ConfigurationPropertiesScan} on the application class.
 * {@code @Validated} makes a nonsensical configuration fail at startup rather than
 * at the first request that trips over it.
 */
@Validated
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(
    @DefaultValue("365") @Min(1) @Max(730) int windowDays,

    @DefaultValue("9") @Min(1) @Max(50) int maxSeats,

    @NotNull @Valid Hold hold) {

  public record Hold(
      @DefaultValue("PT5M") @NotNull Duration ttl,
      @DefaultValue("PT1M") @NotNull Duration sweepInterval,
      @DefaultValue("200") @Min(1) @Max(10_000) int sweepBatchSize) {
  }

  // Flat accessors so call sites read booking.holdTtl() rather than
  // booking.hold().ttl(). The nesting is a YAML concern, not an API concern.

  public Duration holdTtl() {
    return hold.ttl();
  }

  public Duration holdSweepInterval() {
    return hold.sweepInterval();
  }

  public int holdSweepBatchSize() {
    return hold.sweepBatchSize();
  }


}
