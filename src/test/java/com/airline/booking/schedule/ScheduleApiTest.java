package com.airline.booking.schedule;

import com.airline.booking.support.AbstractIntegrationTest;
import com.airline.booking.support.TokenFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Deliberately NOT @Transactional. A rollback-per-test would also wrap the MockMvc
 * call in the test's own transaction, which hides exactly the flush-timing behaviour
 * duplicateFlightNumberIsConflict exists to verify.
 */
@AutoConfigureMockMvc
class ScheduleApiTest extends AbstractIntegrationTest {

    /** Keeps generated flight numbers unique across tests sharing one database. */
    private static final AtomicInteger SEQ = new AtomicInteger(500);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenFactory tokens;

    @Test
    @DisplayName("creating a schedule returns 201 and is readable by id")
    void createSuccess() throws Exception {
        String flightNumber = nextFlightNumber();

        String json = mockMvc.perform(post("/api/admin/schedules")
                        .header("Authorization", tokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(flightNumber, "DXB", "LHR", 0, "A320-01",
                                      "[\"MONDAY\",\"WEDNESDAY\",\"FRIDAY\"]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.flightNumber").value(flightNumber))
                .andExpect(jsonPath("$.origin").value("DXB"))
                .andExpect(jsonPath("$.destination").value("LHR"))
                .andExpect(jsonPath("$.totalSeats").value(180))
                .andExpect(jsonPath("$.daysOfOperation.length()").value(3))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        Object id = com.jayway.jsonpath.JsonPath.read(json, "$.id");

        mockMvc.perform(get("/api/admin/schedules/" + id)
                        .header("Authorization", tokens.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value(flightNumber));
    }

    @Test
    @DisplayName("a duplicate active flight number is 409, not 500")
    void duplicateFlightNumberIsConflict() throws Exception {
        String payload = body(nextFlightNumber(), "DXB", "LHR", 0, "A320-01", "[\"TUESDAY\"]");

        mockMvc.perform(post("/api/admin/schedules")
                        .header("Authorization", tokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/admin/schedules")
                        .header("Authorization", tokens.adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_FLIGHT_NUMBER"));
    }

    @Test
    @DisplayName("origin equal to destination is 400")
    void originEqualsDestination() throws Exception {
        postSchedule(body(nextFlightNumber(), "DXB", "DXB", 0, "A320-01", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("an empty day set is 400")
    void emptyDaySet() throws Exception {
        postSchedule(body(nextFlightNumber(), "DXB", "LHR", 0, "A320-01", "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("validTo before validFrom is 400")
    void invalidValidityRange() throws Exception {
        String payload = """
                {"flightNumber":"%s","origin":"DXB","destination":"LHR",
                 "departureTime":"09:30","arrivalTime":"13:45","arrivalDayOffset":0,
                 "aircraftCode":"A320-01","daysOfOperation":["MONDAY"],
                 "validFrom":"%s","validTo":"%s"}
                """.formatted(nextFlightNumber(),
                              LocalDate.now().plusDays(10), LocalDate.now().plusDays(5));

        postSchedule(payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("a same-day arrival before departure is 400 and names the fix")
    void arrivalBeforeDeparture() throws Exception {
        postSchedule(overnightBody(nextFlightNumber(), 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value(containsString("arrivalDayOffset=1")));
    }

    @Test
    @DisplayName("the same overnight flight with arrivalDayOffset=1 is accepted")
    void overnightFlightAccepted() throws Exception {
        postSchedule(overnightBody(nextFlightNumber(), 1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.arrivalDayOffset").value(1));
    }

    @Test
    @DisplayName("an unknown airport is 404")
    void unknownAirport() throws Exception {
        postSchedule(body(nextFlightNumber(), "ZZZ", "LHR", 0, "A320-01", "[\"MONDAY\"]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AIRPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("an unknown aircraft is 404")
    void unknownAircraft() throws Exception {
        postSchedule(body(nextFlightNumber(), "DXB", "LHR", 0, "NOPE-99", "[\"MONDAY\"]"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AIRCRAFT_NOT_FOUND"));
    }

    @Test
    @DisplayName("a malformed flight number is 400")
    void malformedFlightNumber() throws Exception {
        postSchedule(body("not-a-flight", "DXB", "LHR", 0, "A320-01", "[\"MONDAY\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0]").value(containsString("flightNumber")));
    }

    @Test
    @DisplayName("a customer token cannot create schedules")
    void customerCannotCreate() throws Exception {
        mockMvc.perform(post("/api/admin/schedules")
                        .header("Authorization", tokens.customerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(nextFlightNumber(), "DXB", "LHR", 0, "A320-01", "[\"MONDAY\"]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("listing is paginated")
    void listPaginated() throws Exception {
        mockMvc.perform(get("/api/admin/schedules?page=0&size=2")
                        .header("Authorization", tokens.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").exists());
    }

    // ------------------------------------------------------------------ helpers

    private org.springframework.test.web.servlet.ResultActions postSchedule(String payload)
            throws Exception {
        return mockMvc.perform(post("/api/admin/schedules")
                .header("Authorization", tokens.adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload));
    }

    private static String nextFlightNumber() {
        return "ZZ" + SEQ.incrementAndGet();
    }

    private static String body(String flightNumber, String origin, String destination,
                               int offset, String aircraftCode, String daysJson) {
        return """
                {"flightNumber":"%s","origin":"%s","destination":"%s",
                 "departureTime":"09:30","arrivalTime":"13:45","arrivalDayOffset":%d,
                 "aircraftCode":"%s","daysOfOperation":%s,
                 "validFrom":"%s","validTo":"%s"}
                """.formatted(flightNumber, origin, destination, offset, aircraftCode, daysJson,
                              LocalDate.now(), LocalDate.now().plusDays(400));
    }

    private static String overnightBody(String flightNumber, int offset) {
        return """
                {"flightNumber":"%s","origin":"DXB","destination":"LHR",
                 "departureTime":"21:30","arrivalTime":"06:45","arrivalDayOffset":%d,
                 "aircraftCode":"A320-01","daysOfOperation":["MONDAY"],
                 "validFrom":"%s","validTo":"%s"}
                """.formatted(flightNumber, offset, LocalDate.now(), LocalDate.now().plusDays(300));
    }
}
