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
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SeatMapTest extends AbstractIntegrationTest {

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
    @DisplayName("with nothing booked, every seat is AVAILABLE")
    void allAvailableWhenNothingBooked() throws Exception {
        seatMap("XY101", nextWeekday(DayOfWeek.WEDNESDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(180))
                .andExpect(jsonPath("$.availableSeats").value(180))
                .andExpect(jsonPath("$.rows.length()").value(30))
                .andExpect(jsonPath("$.rows[0].row").value(1))
                .andExpect(jsonPath("$.rows[29].row").value(30))
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("AVAILABLE"));
    }

    @Test
    @DisplayName("row 1 carries labels 1A to 1F in letter order")
    void rowLabelsAndOrdering() throws Exception {
        seatMap("XY101", nextWeekday(DayOfWeek.WEDNESDAY))
                .andExpect(jsonPath("$.rows[0].seats.length()").value(6))
                .andExpect(jsonPath("$.rows[0].seats[0].label").value("1A"))
                .andExpect(jsonPath("$.rows[0].seats[1].label").value("1B"))
                .andExpect(jsonPath("$.rows[0].seats[5].label").value("1F"))
                .andExpect(jsonPath("$.rows[29].seats[5].label").value("30F"));
    }

    @Test
    @DisplayName("booked and held seats render with their own statuses")
    void mergedStatuses() throws Exception {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);
        inventory.occupySeats("XY101", date, List.of("1A"), "BOOKED", null);
        inventory.occupySeats("XY101", date, List.of("1B"), "HELD", Instant.now().plusSeconds(300));

        seatMap("XY101", date)
                .andExpect(jsonPath("$.rows[0].seats[0].status").value("BOOKED"))
                .andExpect(jsonPath("$.rows[0].seats[1].status").value("HELD"))
                .andExpect(jsonPath("$.rows[0].seats[2].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availableSeats").value(178));
    }

    @Test
    @DisplayName("a lapsed hold shows the seat as AVAILABLE")
    void lapsedHoldShowsAvailable() throws Exception {
        LocalDate date = nextWeekday(DayOfWeek.WEDNESDAY);
        inventory.occupySeats("XY101", date, List.of("2A"), "HELD", Instant.now().minusSeconds(60));

        seatMap("XY101", date)
                .andExpect(jsonPath("$.rows[1].seats[0].label").value("2A"))
                .andExpect(jsonPath("$.rows[1].seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availableSeats").value(180));
    }

    @Test
    @DisplayName("an unknown flight number is 404")
    void unknownFlightNumber() throws Exception {
        seatMap("ZZ999", nextWeekday(DayOfWeek.WEDNESDAY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLIGHT_NOT_FOUND"));
    }

    @Test
    @DisplayName("a date the flight does not operate on is 422")
    void nonOperatingDate() throws Exception {
        seatMap("XY101", nextWeekday(DayOfWeek.TUESDAY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FLIGHT_NOT_OPERATING"));
    }

    @Test
    @DisplayName("a date outside the booking window is 422")
    void dateOutsideWindow() throws Exception {
        seatMap("XY101", LocalDate.now().plusDays(380))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_OUT_OF_BOOKING_WINDOW"));
    }

    @Test
    @DisplayName("a smaller aircraft reports its own geometry")
    void smallerAircraftGeometry() throws Exception {
        seatMap("XY202", nextWeekday(DayOfWeek.SATURDAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(72))
                .andExpect(jsonPath("$.rows.length()").value(18))
                .andExpect(jsonPath("$.rows[0].seats.length()").value(4))
                .andExpect(jsonPath("$.rows[17].seats[3].label").value("18D"));
    }

    @Test
    @DisplayName("requesting a seat map NEVER creates a flight_instance row")
    void seatMapDoesNotMaterializeInstance() throws Exception {
        int before = inventory.countInstances();

        seatMap("XY101", nextWeekday(DayOfWeek.WEDNESDAY)).andExpect(status().isOk());
        seatMap("XY201", LocalDate.now().plusDays(45)).andExpect(status().isOk());
        seatMap("XY202", nextWeekday(DayOfWeek.SATURDAY)).andExpect(status().isOk());

        // THE invariant for this task. If this fails, someone replaced the lookup with
        // resolveOrCreate and the lazy strategy is now eager by accident.
        assertThat(inventory.countInstances())
                .as("read paths must not materialize flight instances")
                .isEqualTo(before);
    }

    @Test
    @DisplayName("an unauthenticated request is 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(get("/api/flights/XY101/seat-map")
                        .param("date", nextWeekday(DayOfWeek.WEDNESDAY).toString()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions seatMap(String flightNumber, LocalDate date) throws Exception {
        return mockMvc.perform(get("/api/flights/" + flightNumber + "/seat-map")
                .param("date", date.toString())
                .header("Authorization", tokens.customerToken()));
    }

    private static LocalDate nextWeekday(DayOfWeek day) {
        return LocalDate.now().with(TemporalAdjusters.next(day));
    }
}
