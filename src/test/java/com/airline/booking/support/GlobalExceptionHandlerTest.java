package com.airline.booking.support;

import com.airline.booking.common.DomainException;
import com.airline.booking.common.ErrorCode;
import com.airline.booking.config.ClockConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.airline.booking.security.JwtService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the API's error contract. Uses a throwaway controller declared in the test
 * sources, so the contract is verified before any real endpoint exists.
 *
 * <p>{@code addFilters = false} bypasses the security filter chain: this test is
 * about error rendering, and {@code SecurityConfig} does not exist until Task 4.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ClockConfig.class, GlobalExceptionHandlerTest.ThrowingController.class})
public class GlobalExceptionHandlerTest {

  @Autowired
  private MockMvc mockMvc;

  /**
   * JwtAuthFilter is a Filter, so @WebMvcTest instantiates it, but JwtService is a
   * @Service, which @WebMvcTest excludes. Without this the context fails to load.
   */
  @MockitoBean
  private JwtService jwtService;

  @Test
  @DisplayName("domain exception renders its code and the status carried by that code")
  void domainException() throws Exception {
    mockMvc.perform(get("/throw/domain-404"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("FLIGHT_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value("No such flight"))
        .andExpect(jsonPath("$.details").isArray())
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.timestamp").isNotEmpty());
  }

  @Test
  @DisplayName("conflict carries the offending seat labels in details, in order")
  void domainExceptionWithDetails() throws Exception {
    mockMvc.perform(get("/throw/domain-409-details"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
        .andExpect(jsonPath("$.details.length()").value(2))
        .andExpect(jsonPath("$.details[0]").value("12A"))
        .andExpect(jsonPath("$.details[1]").value("12B"));
  }

  @Test
  @DisplayName("bean validation failure becomes 400 VALIDATION_ERROR naming the field")
  void validationFailure() throws Exception {
    mockMvc.perform(post("/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details").isNotEmpty())
        .andExpect(jsonPath("$.details[0]").value(containsString("flightNumber")));
  }

  @Test
  @DisplayName("unexpected exception returns 500 and leaks nothing about its cause")
  void unexpectedException() throws Exception {
    String body = mockMvc.perform(get("/throw/unexpected"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andReturn()
        .getResponse()
        .getContentAsString();

    // The assertion that matters: the thrown message must not reach the client.
    assertThat(body).doesNotContain("boom");
  }

  // ---------------------------------------------------------------- fixtures

  record ValidatedRequest(@NotBlank String flightNumber) {
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/throw/domain-404")
    void domain404() {
      throw new DomainException(ErrorCode.FLIGHT_NOT_FOUND, "No such flight");
    }

    @GetMapping("/throw/domain-409-details")
    void domain409() {
      throw new DomainException(ErrorCode.SEAT_UNAVAILABLE,
          "Seats no longer available: 12A, 12B",
          List.of("12A", "12B"));
    }

    @PostMapping("/validate")
    void validate(@RequestBody @Valid ValidatedRequest request) {
      // never reached by these tests
    }

    @GetMapping("/throw/unexpected")
    void unexpected() {
      throw new IllegalStateException("boom");
    }
  }



}
