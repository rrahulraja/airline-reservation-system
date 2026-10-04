package com.airline.booking.booking;

import com.airline.booking.flight.FlightInstance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * LIVE SEAT INVENTORY. One row means one seat is currently taken on one flight.
 *
 * <p>Guarded by ux_seat_active, a partial unique index on
 * (flight_instance_id, seat_label) WHERE status IN ('HELD','BOOKED'). That index is the
 * double-booking guarantee, and it is why no row locking is needed when claiming seats.
 *
 * <p>Releasing a seat DELETES the row. There is no RELEASED status, because the absence
 * of a row is what makes the seat sellable again.
 */
@Entity
@Table(name = "seat_assignment")
public class SeatAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_instance_id", nullable = false, updatable = false)
    private FlightInstance flightInstance;

    @Column(name = "seat_label", nullable = false, length = 4, updatable = false)
    private String seatLabel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected SeatAssignment() {
        // required by JPA
    }

    static SeatAssignment create(FlightInstance instance,
                                 Booking booking,
                                 String seatLabel,
                                 SeatStatus status,
                                 Instant expiresAt) {
        SeatAssignment assignment = new SeatAssignment();
        assignment.flightInstance = instance;
        assignment.booking = booking;
        assignment.seatLabel = seatLabel;
        assignment.status = status;
        assignment.expiresAt = expiresAt;
        return assignment;
    }

    /**
     * Promotes a held seat to booked, IN PLACE.
     *
     * <p>In place, never delete-and-reinsert: ux_seat_active covers both HELD and BOOKED,
     * so an update never frees the seat, while a delete followed by an insert opens a
     * window for a competing booking to take it.
     *
     * <p>Package-private so only the booking package can promote a seat.
     */
    void promoteToBooked() {
        this.status = SeatStatus.BOOKED;
        this.expiresAt = null;
    }

    public Long getId() {
        return id;
    }

    public String getSeatLabel() {
        return seatLabel;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
