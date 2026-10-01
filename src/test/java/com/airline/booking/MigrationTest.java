package com.airline.booking;

import com.airline.booking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Flyway migrations actually produce the schema the design relies
 * on. The partial-index assertion is the one that matters: dropping the WHERE
 * clause would still create a valid index, but it would forbid re-booking a
 * released seat and break cancellation.
 */
class MigrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("all domain tables exist")
    void allTablesExist() {
        var tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class);

        assertThat(tables).contains(
                "airport", "aircraft", "app_user", "flight_schedule",
                "flight_instance", "booking", "passenger", "seat_assignment");
    }

    @Test
    @DisplayName("ux_seat_active exists and is a PARTIAL unique index")
    void partialUniqueIndexExists() {
        String definition = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'ux_seat_active'",
                String.class);

        assertThat(definition)
                .as("the double-booking guarantee must be unique AND partial")
                .contains("UNIQUE")
                .contains("WHERE")
                .contains("flight_instance_id")
                .contains("seat_label");
    }

    @Test
    @DisplayName("lazy instance creation is protected by a unique constraint")
    void instanceUniquenessConstraintExists() {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM pg_constraint
                WHERE conname = 'ux_instance_schedule_date' AND contype = 'u'
                """, Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("reference and schedule seed data is loaded")
    void seedDataLoaded() {
        assertThat(count("airport")).isGreaterThanOrEqualTo(6);
        assertThat(count("aircraft")).isGreaterThanOrEqualTo(2);
        assertThat(count("app_user")).isEqualTo(2);
        assertThat(count("flight_schedule")).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("seeded aircraft have differing geometries so capacity is never assumed")
    void seededAircraftHaveDifferentCapacities() {
        var capacities = jdbc.queryForList(
                "SELECT DISTINCT row_count * length(seat_letters) FROM aircraft",
                Integer.class);

        assertThat(capacities).hasSizeGreaterThan(1).contains(180, 72);
    }

    private int count(String table) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
