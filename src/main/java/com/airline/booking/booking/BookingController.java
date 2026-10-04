package com.airline.booking.booking;

import com.airline.booking.booking.dto.BookingResponse;
import com.airline.booking.booking.dto.CreateBookingRequest;
import com.airline.booking.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /**
     * @param user resolves straight to the record the JWT filter placed in the security
     *             context, so learning who is booking costs no database hit.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return bookingService.create(request, user == null ? null : user.userId());
    }

    @GetMapping("/{pnr}")
    public BookingResponse get(@PathVariable String pnr) {
        return bookingService.findByPnr(pnr);
    }

    /**
     * POST .../cancel rather than DELETE: the booking record persists with a changed
     * status, so this is a state transition, not resource removal.
     */
    @PostMapping("/{pnr}/cancel")
    public BookingResponse cancel(@PathVariable String pnr) {
        return bookingService.cancel(pnr);
    }
}
