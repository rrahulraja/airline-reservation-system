package com.airline.booking.flight;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads live seat occupancy for one flight instance.
 *
 * <p>JdbcTemplate rather than a JPA repository: the result is a label-to-status map,
 * not an entity graph.
 */
@Component
public class SeatAssignmentLookup {

    private final JdbcTemplate jdbc;

    public SeatAssignmentLookup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Seat label to status for all active, non-lapsed assignments on the instance.
     *
     * <p>The expires_at filter mirrors the search query: a lapsed hold reads as free
     * immediately, without waiting for the sweep job.
     */
    public Map<String, String> activeStatusesByLabel(long flightInstanceId, Instant now) {
        Map<String, String> result = new HashMap<>();

        jdbc.query("""
                SELECT seat_label, status
                FROM seat_assignment
                WHERE flight_instance_id = ?
                  AND status IN ('HELD', 'BOOKED')
                  AND (expires_at IS NULL OR expires_at > ?)
                """,
                rs -> {
                    result.put(rs.getString("seat_label"), rs.getString("status"));
                },
                flightInstanceId, Timestamp.from(now));

        return result;
    }

    /**
     * The flight_instance id for (schedule, date) if one exists, else null.
     *
     * <p>A LOOKUP ONLY. Deliberately not resolveOrCreate: read paths must never
     * materialize an instance, and this is precisely the place where someone later
     * "helpfully" swaps in the resolver and converts the whole strategy to eager.
     *
     * <p>queryForList rather than queryForObject because "no instance yet" is the normal
     * case here, not an error.
     */
    public Long findInstanceId(long scheduleId, LocalDate date) {
        var ids = jdbc.queryForList("""
                SELECT id FROM flight_instance
                WHERE schedule_id = ? AND flight_date = ?
                """, Long.class, scheduleId, date);

        return ids.isEmpty() ? null : ids.get(0);
    }
}
