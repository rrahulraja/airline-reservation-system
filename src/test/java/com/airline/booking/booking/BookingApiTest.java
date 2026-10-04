package com.airline.booking.booking;

import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.InventoryFixture;
import com.airline.booking.support.TokenFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BookingApiTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenFactory tokens;

    @Autowired
    private InventoryFixture inventory;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tidy() {
        inventory.cleanUp();
    }

    @Test
    @DisplayName("booking one seat returns 201 CONFIRMED and is readable by PNR")
    void createSingleSeat() throws Exception {
        String json = book("XY101", wednesday(), List.of(passenger("R Raja", "12A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.pnr").isNotEmpty())
                .andExpect(jsonPath("$.seats[0]").value("12A"))
                .andExpect(jsonPath("$.passengerCount").value(1))
                .andExpect(jsonPath("$.holdExpiresAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String pnr = com.jayway.jsonpath.JsonPath.read(json, "$.pnr");
        assertThat(pnr).hasSize(6);

        mockMvc.perform(get("/api/bookings/" + pnr)
                        .header("Authorization", tokens.customerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pnr").value(pnr))
                .andExpect(jsonPath("$.flightNumber").value("XY101"));
    }

    @Test
    @DisplayName("booking three seats writes exactly three seat_assignment rows")
    void createMultipleSeats() throws Exception {
        book("XY101", wednesday(), List.of(passenger("A", "14A"), passenger("B", "14B"),
                                           passenger("C", "14C")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()").value(3))
                .andExpect(jsonPath("$.passengerCount").value(3));

        assertThat(countSeatRows()).isEqualTo(3);
    }

    @Test
    @DisplayName("booking creates exactly one flight_instance row")
    void createMaterializesOneInstance() throws Exception {
        int before = inventory.countInstances();

        book("XY101", wednesday(), List.of(passenger("A", "16A"))).andExpect(status().isCreated());

        // Booking IS allowed to materialize; reads are not.
        assertThat(inventory.countInstances()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("booking an already-booked seat is 409 naming the seat, not 500")
    void seatAlreadyBooked() throws Exception {
        LocalDate date = wednesday();
        book("XY101", date, List.of(passenger("First", "20A"))).andExpect(status().isCreated());

        // THE headline assertion of the project. A 500 here means the explicit flush() in
        // SeatClaimService is missing or sits outside the try block.
        book("XY101", date, List.of(passenger("Second", "20A")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.details[0]").value("20A"));
    }

    @Test
    @DisplayName("a partial conflict rolls the whole booking back, leaving no orphan seats")
    void multiSeatPartialConflictRollsBackEverything() throws Exception {
        LocalDate date = wednesday();
        book("XY101", date, List.of(passenger("First", "21A"))).andExpect(status().isCreated());

        int bookingsBefore = countBookings();

        book("XY101", date, List.of(passenger("X", "21B"), passenger("Y", "21A"),
                                    passenger("Z", "21C")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));

        assertThat(countSeatRows()).as("21B and 21C must not have been written").isEqualTo(1);
        assertThat(countBookings()).as("no partial booking was committed").isEqualTo(bookingsBefore);
    }

    @Test
    @DisplayName("the same seat twice in one request is 400, not 409, and writes nothing")
    void duplicateSeatInRequest() throws Exception {
        book("XY101", wednesday(), List.of(passenger("A", "22A"), passenger("B", "22A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("22A")));

        assertThat(countSeatRows()).isZero();
    }

    @Test
    @DisplayName("seat labels are normalized, so lowercase cannot double-book a seat")
    void seatLabelsAreNormalized() throws Exception {
        LocalDate date = wednesday();

        book("XY101", date, List.of(passenger("Lower", " 23a ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats[0]").value("23A"));

        // Without normalization "23A" and "23a" would be different rows in the unique
        // index and this would return 201: the same physical seat sold twice. This is the
        // test that guards that hole.
        book("XY101", date, List.of(passenger("Upper", "23A")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
    }

    @Test
    @DisplayName("a seat that does not exist on the aircraft is 400")
    void invalidSeatLabel() throws Exception {
        for (String label : new String[]{"31A", "1G", "A12", "0A", "01A"}) {
            book("XY101", wednesday(), List.of(passenger("A", label)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("more seats than the configured maximum is 400")
    void tooManySeats() throws Exception {
        List<String> passengers = new ArrayList<>();
        for (int row = 1; row <= 10; row++) {
            passengers.add(passenger("P" + row, row + "A"));
        }

        book("XY101", wednesday(), passengers)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("at most")));
    }

    @Test
    @DisplayName("an unknown flight number is 404")
    void unknownFlight() throws Exception {
        book("ZZ999", wednesday(), List.of(passenger("A", "1A")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FLIGHT_NOT_FOUND"));
    }

    @Test
    @DisplayName("a day the flight does not operate is 422")
    void nonOperatingDay() throws Exception {
        book("XY101", LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.TUESDAY)),
             List.of(passenger("A", "1A")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("FLIGHT_NOT_OPERATING"));
    }

    @Test
    @DisplayName("dates outside the booking window are 422 at both ends")
    void datesOutsideWindow() throws Exception {
        book("XY201", LocalDate.now().plusDays(400), List.of(passenger("A", "1A")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_OUT_OF_BOOKING_WINDOW"));

        book("XY201", LocalDate.now().minusDays(1), List.of(passenger("A", "1A")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DATE_OUT_OF_BOOKING_WINDOW"));
    }

    @Test
    @DisplayName("a seat whose hold has lapsed can be booked without waiting for the sweep")
    void lapsedHoldSeatIsBookable() throws Exception {
        LocalDate date = wednesday();
        inventory.occupySeats("XY101", date, List.of("25A"), "HELD", Instant.now().minusSeconds(60));

        // Proves the purgeLapsedHolds call in BookingService.
        book("XY101", date, List.of(passenger("A", "25A")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats[0]").value("25A"));
    }

    @Test
    @DisplayName("an active hold blocks a booking")
    void activeHoldBlocksBooking() throws Exception {
        LocalDate date = wednesday();
        inventory.occupySeats("XY101", date, List.of("26A"), "HELD", Instant.now().plusSeconds(300));

        book("XY101", date, List.of(passenger("A", "26A")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"));
    }

    @Test
    @DisplayName("a blank passenger name is 400")
    void blankPassengerName() throws Exception {
        book("XY101", wednesday(), List.of(passenger("", "27A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("an unknown PNR is 404")
    void unknownPnr() throws Exception {
        mockMvc.perform(get("/api/bookings/ZZZZZZ")
                        .header("Authorization", tokens.customerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("booking without a token is 401")
    void unauthenticated() throws Exception {
        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("XY101", wednesday(), List.of(passenger("A", "1A")))))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions book(String flightNumber, LocalDate date, List<String> passengers)
            throws Exception {
        return mockMvc.perform(post("/api/bookings")
                .header("Authorization", tokens.customerToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(flightNumber, date, passengers)));
    }

    private static String passenger(String name, String seat) {
        return "{\"fullName\":\"%s\",\"seatLabel\":\"%s\"}".formatted(name, seat);
    }

    private static String body(String flightNumber, LocalDate date, List<String> passengers) {
        return """
                {"flightNumber":"%s","flightDate":"%s","contactName":"Test Booker",
                 "passengers":[%s]}
                """.formatted(flightNumber, date, String.join(",", passengers));
    }

    private int countSeatRows() {
        return jdbc.queryForObject(
                "SELECT count(*) FROM seat_assignment WHERE status IN ('HELD','BOOKED')",
                Integer.class);
    }

    private int countBookings() {
        return jdbc.queryForObject("SELECT count(*) FROM booking", Integer.class);
    }

    private static LocalDate wednesday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
    }
}
