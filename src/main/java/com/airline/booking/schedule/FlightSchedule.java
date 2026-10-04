package com.airline.booking.schedule;

import com.airline.booking.aircraft.Aircraft;
import com.airline.booking.airport.Airport;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A repeating flight: the template from which bookable instances are derived.
 *
 * <p>This is the source of truth for what flies. No {@code flight_instance} row is
 * required for a flight to be searchable; search computes instances from these rows
 * directly.
 *
 * <p>Immutable after creation, so there are no setters.
 */
@Entity
@Table(name = "flight_schedule")
public class FlightSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "flight_number", nullable = false, length = 8, updatable = false)
    private String flightNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_airport_id", nullable = false, updatable = false)
    private Airport sourceAirport;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_airport_id", nullable = false, updatable = false)
    private Airport destinationAirport;

    /** UTC time of day. */
    @Column(name = "departure_time", nullable = false)
    private LocalTime departureTime;

    /** UTC time of day. */
    @Column(name = "arrival_time", nullable = false)
    private LocalTime arrivalTime;

    /** 0 for same-day arrival, 1 when the flight lands the following day. */
    @Column(name = "arrival_day_offset", nullable = false)
    private short arrivalDayOffset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aircraft_id", nullable = false, updatable = false)
    private Aircraft aircraft;

    @Convert(converter = DaysOfOperationConverter.class)
    @Column(name = "days_of_operation", nullable = false)
    private DaysOfOperation daysOfOperation;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    protected FlightSchedule() {
        // required by JPA
    }

    /** The only way application code creates a schedule. */
    public static FlightSchedule create(String flightNumber,
                                        Airport source,
                                        Airport destination,
                                        LocalTime departureTime,
                                        LocalTime arrivalTime,
                                        short arrivalDayOffset,
                                        Aircraft aircraft,
                                        DaysOfOperation daysOfOperation,
                                        LocalDate validFrom,
                                        LocalDate validTo) {
        FlightSchedule schedule = new FlightSchedule();
        schedule.flightNumber = flightNumber;
        schedule.sourceAirport = source;
        schedule.destinationAirport = destination;
        schedule.departureTime = departureTime;
        schedule.arrivalTime = arrivalTime;
        schedule.arrivalDayOffset = arrivalDayOffset;
        schedule.aircraft = aircraft;
        schedule.daysOfOperation = daysOfOperation;
        schedule.validFrom = validFrom;
        schedule.validTo = validTo;
        schedule.active = true;
        return schedule;
    }

    /**
     * Whether this schedule produces a bookable flight on the given date.
     *
     * <p>Lives on the entity because the seat map, booking and hold flows all ask this
     * question, and the answer involves three conditions. Duplicating it is how one
     * caller ends up forgetting the validity range.
     */
    public boolean operatesOn(LocalDate date) {
        return active
               && !date.isBefore(validFrom)
               && !date.isAfter(validTo)
               && daysOfOperation.operatesOn(date.getDayOfWeek());
    }

    public Long getId() {
        return id;
    }

    public String getFlightNumber() {
        return flightNumber;
    }

    public Airport getSourceAirport() {
        return sourceAirport;
    }

    public Airport getDestinationAirport() {
        return destinationAirport;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public short getArrivalDayOffset() {
        return arrivalDayOffset;
    }

    public Aircraft getAircraft() {
        return aircraft;
    }

    public DaysOfOperation getDaysOfOperation() {
        return daysOfOperation;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
