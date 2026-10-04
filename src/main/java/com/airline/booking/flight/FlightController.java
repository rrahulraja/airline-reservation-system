package com.airline.booking.flight;

import com.airline.booking.flight.dto.FlightSearchResult;
import com.airline.booking.flight.dto.SeatMapResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightSearchService searchService;
    private final SeatMapService seatMapService;

    public FlightController(FlightSearchService searchService, SeatMapService seatMapService) {
        this.searchService = searchService;
        this.seatMapService = seatMapService;
    }

    /**
     * @param date ISO date. Without the format annotation a non-ISO value produces a
     *             type-mismatch exception, which the global handler renders as a clean
     *             400 naming the parameter.
     */
    @GetMapping("/search")
    public List<FlightSearchResult> search(
            @RequestParam String origin,
            @RequestParam String destination,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return searchService.search(origin, destination, date);
    }

    /**
     * Addressed by flight number and date, never by instance id: with lazy generation the
     * instance row may not exist while a customer is looking at the flight.
     */
    @GetMapping("/{flightNumber}/seat-map")
    public SeatMapResponse seatMap(
            @PathVariable String flightNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        return seatMapService.seatMap(flightNumber, date);
    }
}
