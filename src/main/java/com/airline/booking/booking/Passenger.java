package com.airline.booking.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A passenger on a booking, and the record of which seat was SOLD to them.
 *
 * <p>Survives cancellation: seat_assignment is live inventory and gets deleted when a
 * seat is released, but this row remains, so the history of what was sold is never lost.
 * That is why seat_label is duplicated across the two tables.
 */
@Entity
@Table(name = "passenger")
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "seat_label", nullable = false, length = 4)
    private String seatLabel;

    protected Passenger() {
        // required by JPA
    }

    static Passenger create(Booking booking, String fullName, String seatLabel) {
        Passenger passenger = new Passenger();
        passenger.booking = booking;
        passenger.fullName = fullName;
        passenger.seatLabel = seatLabel;
        return passenger;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getSeatLabel() {
        return seatLabel;
    }
}
