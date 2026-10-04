package com.airline.booking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Clock whose "now" the test controls.
 *
 * <p>This is what makes hold expiry testable in milliseconds rather than five minutes.
 * Clock.fixed cannot move; this can.
 */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    public MutableClock(Instant initial) {
        this(initial, ZoneOffset.UTC);
    }

    private MutableClock(Instant initial, ZoneId zone) {
        this.now = new AtomicReference<>(initial);
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(now.get(), newZone);
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    public void advance(Duration amount) {
        now.updateAndGet(current -> current.plus(amount));
    }

    public void set(Instant instant) {
        now.set(instant);
    }

    public void setToSystemTime() {
        now.set(Instant.now());
    }
}
