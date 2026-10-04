package com.airline.booking.booking;

import com.airline.booking.booking.dto.BookingResponse;
import com.airline.booking.booking.dto.CreateBookingRequest;
import com.airline.booking.booking.dto.PassengerRequest;
import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import com.airline.booking.common.PnrGenerator;
import com.airline.booking.common.exceptions.BookingNotFoundException;
import com.airline.booking.flight.FlightInstance;
import com.airline.booking.flight.FlightInstanceResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class BookingService {

    private static final int PNR_ATTEMPTS = 3;

    private final BookingRequestValidator validator;
    private final FlightInstanceResolver instanceResolver;
    private final SeatClaimService seatClaimService;
    private final BookingRepository bookingRepository;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final PnrGenerator pnrGenerator;
    private final Clock clock;

    public BookingService(BookingRequestValidator validator,
                          FlightInstanceResolver instanceResolver,
                          SeatClaimService seatClaimService,
                          BookingRepository bookingRepository,
                          SeatAssignmentRepository seatAssignmentRepository,
                          PnrGenerator pnrGenerator,
                          Clock clock) {
        this.validator = validator;
        this.instanceResolver = instanceResolver;
        this.seatClaimService = seatClaimService;
        this.bookingRepository = bookingRepository;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.pnrGenerator = pnrGenerator;
        this.clock = clock;
    }

    /**
     * Creates a CONFIRMED booking in one call, the brief's primary flow.
     *
     * <p>One transaction from validation through seat insert. Any failure rolls the whole
     * thing back, so a partial booking is impossible.
     */
    @Transactional
    public BookingResponse create(CreateBookingRequest request, Long userId) {
        return createInternal(request.flightNumber(), request.flightDate(), request.contactName(),
                              request.passengers(), BookingStatus.CONFIRMED, SeatStatus.BOOKED,
                              null, userId);
    }

    /**
     * Shared by direct booking and hold creation. The only difference between a booking and
     * a hold is the two statuses and the expiry instant, so there is one code path and one
     * concurrency proof.
     */
    @Transactional
    BookingResponse createInternal(String flightNumber,
                                   LocalDate flightDate,
                                   String contactName,
                                   List<PassengerRequest> passengers,
                                   BookingStatus bookingStatus,
                                   SeatStatus seatStatus,
                                   Instant holdExpiresAt,
                                   Long userId) {

        Instant now = clock.instant();

        ResolvedBookingRequest resolved = validator.validate(flightNumber, flightDate, passengers);

        // The only place booking code touches the flight module's internals, and it is
        // race-safe.
        FlightInstance instance = instanceResolver.resolveOrCreate(resolved.schedule(), flightDate);

        // Free any lapsed holds on this flight first, so a booking never fails because of a
        // hold the sweep job has not reached yet.
        seatAssignmentRepository.purgeLapsedHolds(instance.getId(), now);

        Booking booking = Booking.create(nextPnr(), instance, bookingStatus,
                                         (short) resolved.passengers().size(), contactName,
                                         userId, holdExpiresAt, now);

        resolved.passengers().forEach(p -> booking.addPassenger(p.fullName(), p.seatLabel()));

        Booking saved = bookingRepository.save(booking);

        // Claims all seats or throws. Sorted labels, single batch, explicit flush.
        seatClaimService.claimSeats(instance, saved, resolved.sortedSeatLabels(),
                                    seatStatus, holdExpiresAt);

        return BookingResponse.from(saved, resolved.sortedSeatLabels(),
                                    resolved.schedule().getFlightNumber(), flightDate);
    }

    @Transactional(readOnly = true)
    public BookingResponse findByPnr(String pnr) {
        Booking booking = requireByPnr(pnr);
        return BookingResponse.from(booking, activeSeatLabels(booking));
    }

    /**
     * Cancels a booking and releases its seats.
     *
     * <p>Seats are released by DELETING the seat_assignment rows, not by flipping a status.
     * ux_seat_active only covers HELD and BOOKED, so the absence of a row is what makes the
     * seat sellable again: no extra bookkeeping, and no chance of a stale RELEASED row
     * blocking a sale.
     *
     * <p>Passenger rows are deliberately left alone: they are the record of what was sold,
     * and that history survives cancellation.
     *
     * <p>Already-cancelled returns 409 rather than an idempotent 200, because a client
     * cancelling twice has a stale view of the booking and saying so is more useful than
     * silently agreeing.
     */
    @Transactional
    public BookingResponse cancel(String pnr) {
        Booking booking = requireByPnr(pnr);

        if (!booking.getStatus().isCancellable()) {
            throw new DomainException(ErrorCode.BOOKING_NOT_CANCELLABLE,
                    "Booking " + booking.getPnr() + " is " + booking.getStatus()
                    + " and cannot be cancelled");
        }

        // Read the labels BEFORE the delete, so the response can report what was released.
        List<String> releasedSeats = activeSeatLabels(booking);

        booking.cancel(clock.instant());
        seatAssignmentRepository.deleteByBookingId(booking.getId());

        return BookingResponse.from(booking, releasedSeats);
    }

    /** Normalizes the PNR: customers type lowercase, the stored value is uppercase. */
    @Transactional(readOnly = true)
    public Booking requireByPnr(String pnr) {
        String normalized = pnr == null ? "" : pnr.trim().toUpperCase();
        return bookingRepository.findByPnr(normalized)
                                .orElseThrow(() -> new BookingNotFoundException(normalized));
    }

    List<String> activeSeatLabels(Booking booking) {
        return seatAssignmentRepository.findByBookingId(booking.getId()).stream()
                .map(SeatAssignment::getSeatLabel)
                .sorted()
                .toList();
    }

    /**
     * A PNR that is not already taken. The unique index is the real guarantee; this loop
     * only avoids surfacing a once-in-a-billion collision to the client.
     */
    private String nextPnr() {
        for (int attempt = 0; attempt < PNR_ATTEMPTS; attempt++) {
            String candidate = pnrGenerator.generate();
            if (!bookingRepository.existsByPnr(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Could not generate an unused PNR in " + PNR_ATTEMPTS + " attempts");
    }
}
