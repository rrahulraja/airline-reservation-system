package com.airline.booking.flight;

import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.InventoryFixture;
import com.airline.booking.support.TokenFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seeded schedules this relies on (V3__seed_schedules.sql):
 *   XY101  DXB-LHR  Mon/Wed/Fri  mask 21   A320-01  (180 seats)
 *   XY102  LHR-DXB  Mon/Wed/Fri  offset 1  A320-01
 *   XY201  BOM-DEL  daily        mask 127  A320-02  (180 seats)
 *   XY202  DEL-BOM  Sat/Sun      mask 96   ATR72-01 (72 seats)
 */
@AutoConfigureMockMvc
class FlightSearchTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenFactory tokens;

    @Autowired
    private InventoryFixture inventory;

    @AfterEach
    void tidy() {
        inventory.cleanUp();
    }

    @Test
    @DisplayName("a flight is returned on a day it operates, with full availability")
    void operatingDayReturnsFlight() throws Exception {
        search("DXB", "LHR", nextWeekday(DayOfWeek.WEDNESDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value("XY101"))
                .andExpect(jsonPath("$[0].totalSeats").value(180))
                .andExpect(jsonPath("$[0].availableSeats").value(180));
    }

    @Test
    @DisplayName("a non-operating day returns an empty list with 200, not 404")
    void nonOperatingDayReturnsEmpty() throws Exception {
        search("DXB", "LHR", nextWeekday(DayOfWeek.TUESDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("availability drops by the number of booked seats")
    void availabilityReflectsBookedSeats() throws Exception {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);
        inventory.occupySeats("XY101", date, List.of("1A", "1B", "1C"), "BOOKED", null);

        search("DXB", "LHR", date).andExpect(jsonPath("$[0].availableSeats").value(177));
    }

    @Test
    @DisplayName("an active hold occupies a seat")
    void availabilityReflectsActiveHold() throws Exception {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);
        inventory.occupySeats("XY101", date, List.of("2A", "2B"), "HELD",
                              Instant.now().plusSeconds(300));

        search("DXB", "LHR", date).andExpect(jsonPath("$[0].availableSeats").value(178));
    }

    @Test
    @DisplayName("a lapsed hold does NOT occupy a seat, even before the sweep runs")
    void lapsedHoldIsIgnored() throws Exception {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);
        inventory.occupySeats("XY101", date, List.of("3A", "3B"), "HELD",
                              Instant.now().minusSeconds(60));

        // Proves the expires_at predicate in the lateral count. Without it this returns
        // 178 and the sweep job becomes load-bearing for correctness.
        search("DXB", "LHR", date).andExpect(jsonPath("$[0].availableSeats").value(180));
    }

    @Test
    @DisplayName("a date beyond the window is 422 even though the schedule is still valid then")
    void dateBeyondWindowIsUnprocessable() throws Exception {
        // Seeded schedules have valid_to = CURRENT_DATE + 400, so this date is inside
        // the schedule's validity but outside the 365-day window.
        search("DXB", "LHR", LocalDate.now().plusDays(380))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_OUT_OF_BOOKING_WINDOW"));
    }

    @Test
    @DisplayName("a past date is 422")
    void pastDateIsUnprocessable() throws Exception {
        search("DXB", "LHR", LocalDate.now().minusDays(1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_OUT_OF_BOOKING_WINDOW"));
    }

    @Test
    @DisplayName("an unknown airport code is 404")
    void unknownAirportIsNotFound() throws Exception {
        search("ZZZ", "LHR", nextWeekday(DayOfWeek.WEDNESDAY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AIRPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("a malformed airport code is 400")
    void malformedAirportIsBadRequest() throws Exception {
        search("DX", "LHR", nextWeekday(DayOfWeek.WEDNESDAY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("lowercase airport codes work")
    void lowercaseCodesWork() throws Exception {
        search("dxb", "lhr", nextWeekday(DayOfWeek.WEDNESDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].flightNumber").value("XY101"));
    }

    @Test
    @DisplayName("a malformed date is 400, not 500")
    void malformedDateIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/flights/search")
                        .param("origin", "DXB").param("destination", "LHR")
                        .param("date", "not-a-date")
                        .header("Authorization", tokens.customerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("an overnight flight arrives the next day, after its departure")
    void overnightFlightArrivesNextDay() throws Exception {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);

        String json = search("LHR", "DXB", date)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Instant departureAt = Instant.parse(com.jayway.jsonpath.JsonPath.read(json, "$[0].departureAt"));
        Instant arrivalAt = Instant.parse(com.jayway.jsonpath.JsonPath.read(json, "$[0].arrivalAt"));

        assertThat(arrivalAt).as("arrival must follow departure").isAfter(departureAt);
        assertThat(arrivalAt.atZone(ZoneOffset.UTC).toLocalDate()).isEqualTo(date.plusDays(1));
    }

    @Test
    @DisplayName("a smaller aircraft reports its own capacity, not 180")
    void capacityComesFromTheAircraft() throws Exception {
        search("DEL", "BOM", nextWeekday(DayOfWeek.SATURDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].totalSeats").value(72));
    }

    @Test
    @DisplayName("searching never creates a flight_instance row")
    void searchDoesNotMaterializeInstances() throws Exception {
        int before = inventory.countInstances();

        search("DXB", "LHR", nextWeekday(DayOfWeek.WEDNESDAY)).andExpect(status().isOk());
        search("BOM", "DEL", LocalDate.now().plusDays(30)).andExpect(status().isOk());

        // The lazy-generation invariant. If this fails, read traffic is silently
        // materializing instances and the strategy has become eager by accident.
        assertThat(inventory.countInstances())
                .as("read paths must not materialize flight instances")
                .isEqualTo(before);
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions search(String origin, String destination, LocalDate date) throws Exception {
        return mockMvc.perform(get("/api/flights/search")
                .param("origin", origin)
                .param("destination", destination)
                .param("date", date.toString())
                .header("Authorization", tokens.customerToken()));
    }

    /**
     * The next occurrence of a weekday, always inside the window and never today, which
     * avoids interacting with the already-departed rule added with booking.
     */
    private static LocalDate nextWeekday(DayOfWeek day) {
        return LocalDate.now().with(TemporalAdjusters.next(day));
    }
}
