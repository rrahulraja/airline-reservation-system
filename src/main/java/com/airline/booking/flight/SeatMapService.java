package com.airline.booking.flight;

import com.airline.booking.aircraft.Aircraft;
import com.airline.booking.common.exceptions.FlightNotOperatingException;
import com.airline.booking.flight.dto.SeatMapResponse;
import com.airline.booking.flight.dto.SeatMapRow;
import com.airline.booking.flight.dto.SeatStatusView;
import com.airline.booking.schedule.FlightSchedule;
import com.airline.booking.schedule.ScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class SeatMapService {

    private final ScheduleService scheduleService;
    private final SeatAssignmentLookup seatLookup;
    private final BookingWindowValidator windowValidator;
    private final Clock clock;

    public SeatMapService(ScheduleService scheduleService,
                          SeatAssignmentLookup seatLookup,
                          BookingWindowValidator windowValidator,
                          Clock clock) {
        this.scheduleService = scheduleService;
        this.seatLookup = seatLookup;
        this.windowValidator = windowValidator;
        this.clock = clock;
    }

    /**
     * Validation order is deliberate: flight exists, then window, then operating day.
     * Telling someone their date is outside the window is useless if the flight number
     * was a typo.
     *
     * <p>availableSeats is capacity minus the occupancy map's size, so the count and the
     * per-seat statuses are computed from the same data and cannot disagree.
     */
    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(String flightNumber, LocalDate date) {
        FlightSchedule schedule = scheduleService.requireActiveByFlightNumber(flightNumber);

        windowValidator.validate(date);

        if (!schedule.operatesOn(date)) {
            throw new FlightNotOperatingException(flightNumber, date,
                    "not scheduled on " + date.getDayOfWeek());
        }

        Aircraft aircraft = schedule.getAircraft();

        // LOOKUP, never create.
        Long instanceId = seatLookup.findInstanceId(schedule.getId(), date);
        Map<String, String> occupancy = instanceId == null
                ? Map.of()
                : seatLookup.activeStatusesByLabel(instanceId, clock.instant());

        int totalSeats = aircraft.capacity();

        return new SeatMapResponse(flightNumber,
                                   date,
                                   aircraft.getAircraftType(),
                                   totalSeats,
                                   totalSeats - occupancy.size(),
                                   buildRows(aircraft, occupancy));
    }

    private List<SeatMapRow> buildRows(Aircraft aircraft, Map<String, String> occupancy) {
        List<SeatMapRow> rows = new ArrayList<>(aircraft.getRowCount());

        for (int rowNumber = 1; rowNumber <= aircraft.getRowCount(); rowNumber++) {
            List<SeatStatusView> seats = new ArrayList<>(aircraft.getSeatLetters().length());

            for (char letter : aircraft.getSeatLetters().toCharArray()) {
                String label = rowNumber + String.valueOf(letter);
                seats.add(new SeatStatusView(label,
                        occupancy.getOrDefault(label, SeatStatusView.AVAILABLE)));
            }
            rows.add(new SeatMapRow(rowNumber, seats));
        }
        return rows;
    }
}
