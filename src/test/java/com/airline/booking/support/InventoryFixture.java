package com.airline.booking.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Writes seat inventory straight to the database, bypassing the service layer.
 *
 * <p>Needed because search and the seat map must be verified before any booking code
 * exists. seat_assignment has a foreign key to booking, so a placeholder booking row is
 * inserted first.
 *
 * <p>Test support only. Nothing in src/main may write inventory this way.
 */
@Component
public class InventoryFixture {

    private final JdbcTemplate jdbc;

    @Autowired
    public InventoryFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long scheduleIdOf(String flightNumber) {
        return jdbc.queryForObject(
                "SELECT id FROM flight_schedule WHERE flight_number = ? AND active",
                Long.class, flightNumber);
    }

    /**
     * Ensures a flight_instance row exists for (schedule, date) and returns its id,
     * using the same ON CONFLICT upsert the production resolver uses.
     */
    @Transactional
    public long ensureInstance(long scheduleId, LocalDate date) {
        LocalTime departureTime = jdbc.queryForObject(
                "SELECT departure_time FROM flight_schedule WHERE id = ?", LocalTime.class, scheduleId);
        LocalTime arrivalTime = jdbc.queryForObject(
                "SELECT arrival_time FROM flight_schedule WHERE id = ?", LocalTime.class, scheduleId);
        Short offset = jdbc.queryForObject(
                "SELECT arrival_day_offset FROM flight_schedule WHERE id = ?", Short.class, scheduleId);

        Instant departureAt = date.atTime(departureTime).toInstant(ZoneOffset.UTC);
        Instant arrivalAt = date.plusDays(offset).atTime(arrivalTime).toInstant(ZoneOffset.UTC);

        jdbc.update("""
                INSERT INTO flight_instance (schedule_id, flight_date, departure_at, arrival_at, status)
                VALUES (?, ?, ?, ?, 'SCHEDULED')
                ON CONFLICT (schedule_id, flight_date) DO NOTHING
                """, scheduleId, date, Timestamp.from(departureAt), Timestamp.from(arrivalAt));

        return jdbc.queryForObject(
                "SELECT id FROM flight_instance WHERE schedule_id = ? AND flight_date = ?",
                Long.class, scheduleId, date);
    }

    /**
     * Occupies seats with no service involvement.
     *
     * @param status    "HELD" or "BOOKED"
     * @param expiresAt hold expiry; null for a confirmed booking. A past instant
     *                  simulates a lapsed hold.
     * @return the flight_instance id
     */
    @Transactional
    public long occupySeats(String flightNumber,
                           LocalDate date,
                           List<String> seatLabels,
                           String status,
                           Instant expiresAt) {

        long scheduleId = scheduleIdOf(flightNumber);
        long instanceId = ensureInstance(scheduleId, date);

        String pnr = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String bookingStatus = "HELD".equals(status) ? "HELD" : "CONFIRMED";

        jdbc.update("""
                INSERT INTO booking (pnr, flight_instance_id, status, passenger_count,
                                     contact_name, hold_expires_at)
                VALUES (?, ?, ?, ?, 'fixture', ?)
                """, pnr, instanceId, bookingStatus, seatLabels.size(),
                expiresAt == null ? null : Timestamp.from(expiresAt));

        long bookingId = jdbc.queryForObject(
                "SELECT id FROM booking WHERE pnr = ?", Long.class, pnr);

        for (String label : seatLabels) {
            jdbc.update("""
                    INSERT INTO seat_assignment (flight_instance_id, seat_label, booking_id,
                                                 status, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """, instanceId, label, bookingId, status,
                    expiresAt == null ? null : Timestamp.from(expiresAt));
        }

        return instanceId;
    }

    public int countInstances() {
        return jdbc.queryForObject("SELECT count(*) FROM flight_instance", Integer.class);
    }

    public int countActiveSeats(long instanceId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM seat_assignment
                WHERE flight_instance_id = ? AND status IN ('HELD','BOOKED')
                """, Integer.class, instanceId);
    }

    /**
     * Removes everything this fixture created, leaving seeded data intact.
     *
     * <p>Needed because MockMvc requests commit in their own transactions, so nothing
     * rolls back. Deletion order follows the foreign keys.
     */
    @Transactional
    public void cleanUp() {
        jdbc.update("DELETE FROM seat_assignment");
        jdbc.update("DELETE FROM passenger");
        jdbc.update("DELETE FROM booking");
        jdbc.update("DELETE FROM flight_instance");
    }
}
