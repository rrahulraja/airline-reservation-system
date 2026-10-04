package com.airline.booking.booking;

import com.airline.booking.flight.FlightInstance;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A booking, in any of its four states.
 *
 * <p>A hold is a booking with status HELD and a non-null holdExpiresAt, NOT a separate
 * entity. One lifecycle means confirm and cancel operate uniformly on held and confirmed
 * bookings, and it means the whole hold feature can be removed by deleting one enum value
 * and two endpoints.
 *
 * <p>State transitions are methods on the entity, each taking "now" from the caller's
 * clock, so no service sets status directly and an illegal combination such as CANCELLED
 * with a live hold expiry cannot be constructed.
 */
@Entity
@Table(name = "booking")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 6, updatable = false)
    private String pnr;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flight_instance_id", nullable = false, updatable = false)
    private FlightInstance flightInstance;

    /**
     * STRING, never ORDINAL: the column has a CHECK constraint listing the names, and
     * ORDINAL would write integers that every insert would fail on.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BookingStatus status;

    @Column(name = "passenger_count", nullable = false)
    private short passengerCount;

    @Column(name = "contact_name", length = 120)
    private String contactName;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.LAZY)
    private List<Passenger> passengers = new ArrayList<>();

    protected Booking() {
        // required by JPA
    }

    public static Booking create(String pnr,
                                 FlightInstance instance,
                                 BookingStatus status,
                                 short passengerCount,
                                 String contactName,
                                 Long createdByUserId,
                                 Instant holdExpiresAt,
                                 Instant now) {
        Booking booking = new Booking();
        booking.pnr = pnr;
        booking.flightInstance = instance;
        booking.status = status;
        booking.passengerCount = passengerCount;
        booking.contactName = contactName;
        booking.createdByUserId = createdByUserId;
        booking.holdExpiresAt = holdExpiresAt;
        booking.updatedAt = now;
        return booking;
    }

    public void addPassenger(String fullName, String seatLabel) {
        passengers.add(Passenger.create(this, fullName, seatLabel));
    }

    public void confirm(Instant now) {
        this.status = BookingStatus.CONFIRMED;
        this.holdExpiresAt = null;
        this.updatedAt = now;
    }

    public void cancel(Instant now) {
        this.status = BookingStatus.CANCELLED;
        this.holdExpiresAt = null;
        this.updatedAt = now;
    }

    public void expire(Instant now) {
        this.status = BookingStatus.EXPIRED;
        this.holdExpiresAt = null;
        this.updatedAt = now;
    }

    public boolean holdHasLapsed(Instant now) {
        return holdExpiresAt != null && !holdExpiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getPnr() {
        return pnr;
    }

    public FlightInstance getFlightInstance() {
        return flightInstance;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public short getPassengerCount() {
        return passengerCount;
    }

    public String getContactName() {
        return contactName;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** A copy, so callers cannot mutate the managed collection and trigger orphanRemoval. */
    public List<Passenger> getPassengers() {
        return List.copyOf(passengers);
    }
}
