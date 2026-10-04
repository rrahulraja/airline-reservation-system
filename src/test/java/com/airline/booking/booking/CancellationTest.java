package com.airline.booking.booking;

import com.airline.booking.booking.dto.CreateBookingRequest;
import com.airline.booking.booking.dto.PassengerRequest;
import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.ConcurrentRunner;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CancellationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenFactory tokens;

    @Autowired
    private InventoryFixture inventory;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tidy() {
        inventory.cleanUp();
    }

    @Test
    @DisplayName("cancelling sets CANCELLED, deletes seat rows, and keeps passenger history")
    void cancelReleasesSeatsAndKeepsHistory() throws Exception {
        String pnr = bookSeat(wednesday(), "20A");

        cancel(pnr)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.seats[0]").value("20A"));

        assertThat(bookingStatus(pnr)).as("the booking row survives").isEqualTo("CANCELLED");
        assertThat(activeSeatRows(pnr)).as("live inventory is gone").isZero();
        assertThat(passengerSeatLabels(pnr))
                .as("history is preserved on the passenger row")
                .containsExactly("20A");
    }

    @Test
    @DisplayName("a cancelled seat can immediately be booked again")
    void cancelThenRebookSameSeat() throws Exception {
        LocalDate date = wednesday();
        String pnr = bookSeat(date, "21A");

        cancel(pnr).andExpect(status().isOk());

        // Catches releasing seats the wrong way. Had release been a status change to
        // RELEASED, the partial index would still hold the seat and this would be a 409.
        mockMvc.perform(post("/api/bookings")
                        .header("Authorization", tokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rebookBody(date, "21A")))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("cancelling twice is 409")
    void cancelTwice() throws Exception {
        String pnr = bookSeat(wednesday(), "22A");

        cancel(pnr).andExpect(status().isOk());
        cancel(pnr)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_CANCELLABLE"));
    }

    @Test
    @DisplayName("cancelling an unknown PNR is 404")
    void cancelUnknownPnr() throws Exception {
        cancel("ZZZZZZ")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    @DisplayName("a released seat shows AVAILABLE on the seat map")
    void releasedSeatIsAvailableOnSeatMap() throws Exception {
        LocalDate date = wednesday();
        String pnr = bookSeat(date, "23A");

        cancel(pnr).andExpect(status().isOk());

        mockMvc.perform(get("/api/flights/XY101/seat-map")
                        .param("date", date.toString())
                        .header("Authorization", tokens.customerToken()))
                .andExpect(jsonPath("$.rows[22].seats[0].label").value("23A"))
                .andExpect(jsonPath("$.rows[22].seats[0].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.availableSeats").value(180));
    }

    @Test
    @DisplayName("a released seat restores search availability")
    void releasedSeatRestoresSearchAvailability() throws Exception {
        LocalDate date = wednesday();
        String pnr = bookSeat(date, "24A");

        cancel(pnr).andExpect(status().isOk());

        mockMvc.perform(get("/api/flights/search")
                        .param("origin", "DXB").param("destination", "LHR")
                        .param("date", date.toString())
                        .header("Authorization", tokens.customerToken()))
                .andExpect(jsonPath("$[0].availableSeats").value(180));
    }

    @Test
    @DisplayName("cancelling without a token is 401")
    void cancelUnauthenticated() throws Exception {
        String pnr = bookSeat(wednesday(), "25A");

        mockMvc.perform(post("/api/bookings/" + pnr + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("10 concurrent cancellations of one booking: exactly one succeeds")
    void concurrentCancelExactlyOneSucceeds() throws Exception {
        String pnr = bookSeat(wednesday(), "26A");

        var outcome = ConcurrentRunner.runAll(10, () -> bookingService.cancel(pnr));

        // THE ONLY TEST that exercises the @Version layer of the concurrency strategy.
        // Without it, @Version is mapped but never proven, and a lost update on a status
        // transition would go unnoticed. The nine failures may be a mix of
        // BOOKING_NOT_CANCELLABLE and OptimisticLockingFailureException; both are correct,
        // which is why this counts failures rather than demanding one type.
        assertThat(outcome.successCount()).as("exactly one cancellation may win").isEqualTo(1);
        assertThat(outcome.failureCount()).isEqualTo(9);

        assertThat(bookingStatus(pnr)).isEqualTo("CANCELLED");
        assertThat(activeSeatRows(pnr)).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions cancel(String pnr) throws Exception {
        return mockMvc.perform(post("/api/bookings/" + pnr + "/cancel")
                .header("Authorization", tokens.customerToken()));
    }

    private String bookSeat(LocalDate date, String seat) {
        return bookingService.create(new CreateBookingRequest(
                "XY101", date, "Cancellation Test",
                List.of(new PassengerRequest("Passenger " + seat, seat))), null).pnr();
    }

    private static String rebookBody(LocalDate date, String seat) {
        return """
                {"flightNumber":"XY101","flightDate":"%s","contactName":"Rebooker",
                 "passengers":[{"fullName":"Second","seatLabel":"%s"}]}
                """.formatted(date, seat);
    }

    private String bookingStatus(String pnr) {
        return jdbc.queryForObject("SELECT status FROM booking WHERE pnr = ?", String.class, pnr);
    }

    private int activeSeatRows(String pnr) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM seat_assignment sa
                JOIN booking b ON b.id = sa.booking_id WHERE b.pnr = ?
                """, Integer.class, pnr);
    }

    private List<String> passengerSeatLabels(String pnr) {
        return jdbc.queryForList("""
                SELECT p.seat_label FROM passenger p
                JOIN booking b ON b.id = p.booking_id WHERE b.pnr = ? ORDER BY p.seat_label
                """, String.class, pnr);
    }

    private static LocalDate wednesday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
    }
}
