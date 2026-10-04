package com.airline.booking.booking;

import com.airline.booking.booking.dto.BookingResponse;
import com.airline.booking.booking.dto.CreateHoldRequest;
import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import com.airline.booking.config.BookingProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Temporary seat holds and their promotion to confirmed bookings.
 *
 * <p>Delegates creation to BookingService.createInternal, so a hold travels the exact same
 * validation and seat-claim path as a direct booking. One code path means the concurrency
 * suite covers holds too, without a second set of tests.
 */
@Service
public class HoldService {

    private final BookingService bookingService;
    private final SeatAssignmentRepository seatAssignmentRepository;
    private final BookingProperties properties;
    private final Clock clock;

    public HoldService(BookingService bookingService,
                       SeatAssignmentRepository seatAssignmentRepository,
                       BookingProperties properties,
                       Clock clock) {
        this.bookingService = bookingService;
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse hold(CreateHoldRequest request, Long userId) {
        Instant expiresAt = clock.instant().plus(properties.holdTtl());

        return bookingService.createInternal(request.flightNumber(), request.flightDate(),
                                             request.contactName(), request.passengers(),
                                             BookingStatus.HELD, SeatStatus.HELD,
                                             expiresAt, userId);
    }

    /**
     * Promotes a held booking to confirmed.
     *
     * <p>Updates the existing seat_assignment rows IN PLACE. Because ux_seat_active covers
     * both HELD and BOOKED, the seat is never momentarily free during the flip, whereas
     * delete-then-insert would open a window for a competing booking to take it.
     */
    @Transactional
    public BookingResponse confirm(String pnr) {
        Booking booking = bookingService.requireByPnr(pnr);
        Instant now = clock.instant();

        if (!booking.getStatus().isConfirmable()) {
            throw new DomainException(ErrorCode.BOOKING_NOT_CONFIRMABLE,
                    "Booking " + booking.getPnr() + " is " + booking.getStatus()
                    + " and cannot be confirmed");
        }

        if (booking.holdHasLapsed(now)) {
            throw new DomainException(ErrorCode.HOLD_EXPIRED,
                    "The hold on booking " + booking.getPnr() + " expired at "
                    + booking.getHoldExpiresAt());
        }

        List<SeatAssignment> assignments = seatAssignmentRepository.findByBookingId(booking.getId());

        assignments.forEach(SeatAssignment::promoteToBooked);
        booking.confirm(now);

        List<String> seats = assignments.stream()
                .map(SeatAssignment::getSeatLabel)
                .sorted()
                .toList();

        return BookingResponse.from(booking, seats);
    }
}
