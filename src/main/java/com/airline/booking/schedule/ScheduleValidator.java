package com.airline.booking.schedule;

import com.airline.booking.common.exceptions.ValidationException;
import com.airline.booking.schedule.dto.CreateScheduleRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The cross-field rules bean validation cannot express.
 *
 * <p>Collects every violation before throwing, so an admin fixing a bad request sees
 * all the problems at once rather than one round trip at a time.
 */
@Component
public class ScheduleValidator {

    public void validate(CreateScheduleRequest request) {
        List<String> violations = new ArrayList<>();

        if (request.origin().equalsIgnoreCase(request.destination())) {
            violations.add("destination: must differ from origin");
        }

        if (request.validTo().isBefore(request.validFrom())) {
            violations.add("validTo: must not be before validFrom");
        }

        if (request.daysOfOperation() != null && request.daysOfOperation().isEmpty()) {
            violations.add("daysOfOperation: must contain at least one day");
        }

        // A same-day arrival that precedes departure is a data-entry error. Without
        // this check a typo creates a flight that arrives before it departs, and every
        // arrival_at computed from it is wrong. With arrivalDayOffset = 1 it is a
        // legitimate overnight flight.
        if (request.arrivalDayOffset() == 0
            && request.arrivalTime() != null
            && request.departureTime() != null
            && !request.arrivalTime().isAfter(request.departureTime())) {
            violations.add("arrivalTime: must be after departureTime, "
                           + "or set arrivalDayOffset=1 for an overnight flight");
        }

        if (!violations.isEmpty()) {
            throw new ValidationException("Schedule validation failed", violations);
        }
    }
}
