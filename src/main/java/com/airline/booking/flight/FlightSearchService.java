package com.airline.booking.flight;

import com.airline.booking.airport.Airport;
import com.airline.booking.airport.AirportService;
import com.airline.booking.flight.dto.FlightSearchResult;
import com.airline.booking.schedule.DaysOfOperation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class FlightSearchService {

    private final FlightSearchRepository repository;
    private final AirportService airportService;
    private final BookingWindowValidator windowValidator;

    public FlightSearchService(FlightSearchRepository repository,
                               AirportService airportService,
                               BookingWindowValidator windowValidator) {
        this.repository = repository;
        this.airportService = airportService;
        this.windowValidator = windowValidator;
    }

    /**
     * A non-operating day returns an empty list with status 200, not a 404: the route
     * exists, it simply does not fly that day.
     *
     * <p>Airports are resolved before the window is checked so that an unknown airport
     * is reported ahead of a date problem, which is the first error a client can act on.
     */
    @Transactional(readOnly = true)
    public List<FlightSearchResult> search(String origin, String destination, LocalDate date) {
        Airport source = airportService.resolveByIata(origin);
        Airport dest = airportService.resolveByIata(destination);

        windowValidator.validate(date);

        short dayBit = DaysOfOperation.bitFor(date.getDayOfWeek());

        return repository.search(source.getId(), dest.getId(), date, dayBit).stream()
                         .map(row -> toResult(row, source, dest, date))
                         .toList();
    }

    private FlightSearchResult toResult(FlightSearchRow row,
                                        Airport source,
                                        Airport destination,
                                        LocalDate date) {
        int totalSeats = row.getRowCount() * row.getSeatLetters().length();
        int occupied = row.getOccupied() == null ? 0 : row.getOccupied();

        return new FlightSearchResult(
                row.getFlightNumber(),
                source.getIataCode(),
                destination.getIataCode(),
                date,
                date.atTime(row.getDepartureTime()).toInstant(ZoneOffset.UTC),
                // The day offset must be applied BEFORE the time, or an overnight
                // flight computes an arrival hours before its departure.
                date.plusDays(row.getArrivalDayOffset())
                    .atTime(row.getArrivalTime())
                    .toInstant(ZoneOffset.UTC),
                row.getAircraftType(),
                totalSeats,
                totalSeats - occupied);
    }
}
