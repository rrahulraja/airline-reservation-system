package com.airline.booking.booking;

import com.airline.booking.aircraft.Aircraft;
import com.airline.booking.aircraft.SeatMapGenerator;
import com.airline.booking.booking.dto.PassengerRequest;
import com.airline.booking.common.exceptions.FlightNotOperatingException;
import com.airline.booking.common.exceptions.InvalidSeatLabelException;
import com.airline.booking.common.exceptions.ValidationException;
import com.airline.booking.config.BookingProperties;
import com.airline.booking.flight.BookingWindowValidator;
import com.airline.booking.schedule.FlightSchedule;
import com.airline.booking.schedule.ScheduleService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Every rule a booking or hold request must satisfy, in one place.
 *
 * <p>Shared by booking and hold creation. Two divergent copies of these rules is the most
 * likely way this codebase would rot, and a reviewer notices immediately.
 */
@Component
public class BookingRequestValidator {

    private final ScheduleService scheduleService;
    private final SeatMapGenerator seatMapGenerator;
    private final BookingWindowValidator windowValidator;
    private final BookingProperties properties;
    private final Clock clock;

    public BookingRequestValidator(ScheduleService scheduleService,
                                   SeatMapGenerator seatMapGenerator,
                                   BookingWindowValidator windowValidator,
                                   BookingProperties properties,
                                   Clock clock) {
        this.scheduleService = scheduleService;
        this.seatMapGenerator = seatMapGenerator;
        this.windowValidator = windowValidator;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Validates in a deliberate order, most fundamental first, so the client learns about
     * the problem it can actually act on.
     */
    public ResolvedBookingRequest validate(String flightNumber,
                                           LocalDate flightDate,
                                           List<PassengerRequest> passengers) {

        // 1. The flight must exist and be active. 404.
        FlightSchedule schedule = scheduleService.requireActiveByFlightNumber(flightNumber);

        // 2. The date must be inside the booking window. 422.
        windowValidator.validate(flightDate);

        // 3. The flight must operate on that date, within its validity range. 422.
        if (!schedule.operatesOn(flightDate)) {
            throw new FlightNotOperatingException(flightNumber, flightDate,
                    "not scheduled on " + flightDate.getDayOfWeek());
        }

        // 4. The flight must not have departed already. 422.
        //
        //    The booking window permits "today", which without this check would let a
        //    customer book a flight that left hours ago. The spec does not require this;
        //    it is a decision recorded in the README's assumptions.
        Instant departureAt = flightDate.atTime(schedule.getDepartureTime())
                                        .toInstant(ZoneOffset.UTC);
        if (!departureAt.isAfter(clock.instant())) {
            throw new FlightNotOperatingException(flightNumber, flightDate,
                    "departure at " + departureAt + " has already passed");
        }

        // 5. Seat count within policy. 400.
        if (passengers.size() > properties.maxSeats()) {
            throw new ValidationException(
                    "A single booking may contain at most " + properties.maxSeats()
                    + " seats, got " + passengers.size());
        }

        Aircraft aircraft = schedule.getAircraft();

        List<String> invalid = new ArrayList<>();
        List<ResolvedBookingRequest.PassengerAssignment> assignments = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (PassengerRequest passenger : passengers) {
            // 6. Normalize the label, and 7. validate it against the aircraft. 400.
            //
            //    Normalization is a CORRECTNESS feature, not a convenience:
            //    ux_seat_active enforces uniqueness on the exact stored string, so if one
            //    request stored "12A" and another "12a", the index would permit both and
            //    the same physical seat would be sold twice.
            var normalized = seatMapGenerator.normalize(passenger.seatLabel());

            if (normalized.isEmpty()
                || !seatMapGenerator.isValidFor(normalized.get(),
                                                aircraft.getRowCount(),
                                                aircraft.getSeatLetters())) {
                invalid.add(passenger.seatLabel());
                continue;
            }

            String label = normalized.get();

            // 8. No duplicate seat within one request. 400, before touching the database.
            //
            //    Without this the duplicate reaches the unique index and surfaces as a
            //    confusing 409 claiming someone else took a seat nobody else booked.
            if (!seen.add(label)) {
                duplicates.add(label);
                continue;
            }

            assignments.add(new ResolvedBookingRequest.PassengerAssignment(
                    passenger.fullName(), label));
        }

        if (!invalid.isEmpty()) {
            throw new InvalidSeatLabelException(invalid, aircraft.getAircraftType());
        }

        if (!duplicates.isEmpty()) {
            throw new ValidationException(
                    "The same seat was requested more than once: " + String.join(", ", duplicates),
                    duplicates);
        }

        // 9. SORTED. A consistent lock-acquisition order across transactions removes the
        //    deadlock case between requests whose seat sets overlap in different orders.
        List<String> sortedLabels = assignments.stream()
                .map(ResolvedBookingRequest.PassengerAssignment::seatLabel)
                .sorted()
                .toList();

        return new ResolvedBookingRequest(schedule, sortedLabels, assignments);
    }
}
