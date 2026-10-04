package com.airline.booking.flight;

import com.airline.booking.flight.dto.FlightSearchResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/flights")
public class FlightController {

    private final FlightSearchService searchService;

    public FlightController(FlightSearchService searchService) {
        this.searchService = searchService;
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
}
