package com.airline.booking.flight;

import com.airline.booking.schedule.FlightSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One bookable occurrence of a schedule on a specific date.
 *
 * <p>Created LAZILY, only when a booking or hold needs something to attach seats to.
 * Most flights in the 365-day window never get a row, which is the point: the table
 * holds instances that matter rather than 100,000 empty ones.
 *
 * <p>departure_at and arrival_at are denormalized from the schedule at creation time so
 * that booking-time checks need no recomputation.
 *
 * <p>No factory method and no setters: rows are created by the native upsert in the
 * repository, not by Hibernate, so this entity is read-only from the application's point
 * of view. That is a direct consequence of needing ON CONFLICT.
 */
@Entity
@Table(name = "flight_instance")
public class FlightInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
    private FlightSchedule schedule;

    @Column(name = "flight_date", nullable = false, updatable = false)
    private LocalDate flightDate;

    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    @Column(name = "arrival_at", nullable = false)
    private Instant arrivalAt;

    @Column(nullable = false, length = 16)
    private String status;

    /**
     * Optimistic lock version. Not used on the booking happy path, where the partial
     * unique index does that work, but it guards status transitions.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected FlightInstance() {
        // required by JPA
    }

    public Long getId() {
        return id;
    }

    public FlightSchedule getSchedule() {
        return schedule;
    }

    public LocalDate getFlightDate() {
        return flightDate;
    }

    public Instant getDepartureAt() {
        return departureAt;
    }

    public Instant getArrivalAt() {
        return arrivalAt;
    }

    public String getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
