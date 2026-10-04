package com.airline.booking.booking;

/**
 * Keeping these predicates on the enum puts the state machine in one place. Cancellation
 * and confirmation both ask these questions, and duplicating the conditions in two
 * services is how the two drift apart.
 */
public enum BookingStatus {
    HELD,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    public boolean isCancellable() {
        return this == HELD || this == CONFIRMED;
    }

    public boolean isConfirmable() {
        return this == HELD;
    }
}
