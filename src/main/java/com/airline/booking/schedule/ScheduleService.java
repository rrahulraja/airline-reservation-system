package com.airline.booking.schedule;

import com.airline.booking.aircraft.Aircraft;
import com.airline.booking.aircraft.AircraftRepository;
import com.airline.booking.airport.Airport;
import com.airline.booking.airport.AirportService;
import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import com.airline.booking.common.exceptions.FlightNotFoundException;
import com.airline.booking.schedule.dto.CreateScheduleRequest;
import com.airline.booking.schedule.dto.ScheduleResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleService {

    private final ScheduleRepository repository;
    private final AirportService airportService;
    private final AircraftRepository aircraftRepository;
    private final ScheduleValidator validator;

    public ScheduleService(ScheduleRepository repository,
                           AirportService airportService,
                           AircraftRepository aircraftRepository,
                           ScheduleValidator validator) {
        this.repository = repository;
        this.airportService = airportService;
        this.aircraftRepository = aircraftRepository;
        this.validator = validator;
    }

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        validator.validate(request);

        // 404s for unknown references propagate from here.
        Airport source = airportService.resolveByIata(request.origin());
        Airport destination = airportService.resolveByIata(request.destination());
        Aircraft aircraft = aircraftRepository.findByAircraftCode(request.aircraftCode())
                .orElseThrow(() -> new DomainException(ErrorCode.AIRCRAFT_NOT_FOUND,
                        "No aircraft with code " + request.aircraftCode()));

        // Checked up front so the common case gets a clean 409. NOT sufficient on its
        // own: between this check and the insert another transaction can commit the
        // same number, which is what the catch below handles.
        if (repository.existsByFlightNumberAndActiveTrue(request.flightNumber())) {
            throw duplicateFlightNumber(request.flightNumber());
        }

        FlightSchedule schedule = FlightSchedule.create(
                request.flightNumber(),
                source,
                destination,
                request.departureTime(),
                request.arrivalTime(),
                request.arrivalDayOffset(),
                aircraft,
                DaysOfOperation.of(request.daysOfOperation()),
                request.validFrom(),
                request.validTo());

        try {
            // saveAndFlush, not save: save queues the insert and Hibernate flushes at
            // commit, which happens AFTER this method returns, so the violation would
            // escape this catch and surface as a 500.
            FlightSchedule saved = repository.saveAndFlush(schedule);
            return ScheduleResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            // The partial unique index ux_schedule_active_flight_number is the real
            // guard; the exists() check above is only a nicer error for the common case.
            throw duplicateFlightNumber(request.flightNumber());
        }
    }

    @Transactional(readOnly = true)
    public ScheduleResponse findById(long id) {
        return repository.findById(id)
                         .map(ScheduleResponse::from)
                         .orElseThrow(() -> new DomainException(ErrorCode.FLIGHT_NOT_FOUND,
                                 "No flight schedule with id " + id));
    }

    @Transactional(readOnly = true)
    public Page<ScheduleResponse> findPage(Pageable pageable) {
        return repository.findAllByActiveTrue(pageable).map(ScheduleResponse::from);
    }

    @Transactional(readOnly = true)
    public FlightSchedule requireActiveByFlightNumber(String flightNumber) {
        return repository.findByFlightNumberAndActiveTrue(flightNumber)
                         .orElseThrow(() -> new FlightNotFoundException(flightNumber));
    }

    private static DomainException duplicateFlightNumber(String flightNumber) {
        return new DomainException(ErrorCode.DUPLICATE_FLIGHT_NUMBER,
                "An active schedule already exists for flight number " + flightNumber);
    }
}
