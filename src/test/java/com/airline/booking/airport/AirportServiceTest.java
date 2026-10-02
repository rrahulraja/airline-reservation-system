package com.airline.booking.airport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.airline.booking.common.ErrorCode;
import com.airline.booking.common.exceptions.AirportNotFoundException;
import com.airline.booking.common.exceptions.ValidationException;
import com.airline.booking.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class AirportServiceTest extends AbstractIntegrationTest {

  @Autowired
  private AirportService service;

  @Test
  @DisplayName("resolves a seeded airport by exact code")
  void resolveExact() {
    var airport = service.resolveByIata("DXB");

    assertThat(airport.getIataCode()).isEqualTo("DXB");
    assertThat(airport.getCity()).isEqualTo("Dubai");
  }

  @Test
  @DisplayName("accepts lowercase and surrounding whitespace")
  void resolveLowercaseAndWhitespace() {
    assertThat(service.resolveByIata(" dxb ").getIataCode()).isEqualTo("DXB");
    assertThat(service.resolveByIata("lhr").getIataCode()).isEqualTo("LHR");
  }

  @Test
  @DisplayName("a well-formed but unknown code is 404, not 400")
  void resolveUnknownIs404() {
    assertThatThrownBy(() -> service.resolveByIata("ZZZ"))
        .isInstanceOf(AirportNotFoundException.class)
        .extracting(e -> ((AirportNotFoundException) e).code())
        .isEqualTo(ErrorCode.AIRPORT_NOT_FOUND);
  }

  @Test
  @DisplayName("a malformed code is 400, not 404")
  void resolveMalformedIs400() {
    for (String bad : new String[]{"DX", "DXBX", "D1B", "", null}) {
      assertThatThrownBy(() -> service.resolveByIata(bad))
          .as("input: %s", bad)
          .isInstanceOf(ValidationException.class)
          .extracting(e -> ((ValidationException) e).code())
          .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
  }

  @Test
  @DisplayName("all seeded airports are listed")
  void findAllReturnsSeededAirports() {
    assertThat(service.findAll())
        .extracting(Airport::getIataCode)
        .contains("DXB", "LHR", "BOM", "DEL", "SIN", "JFK");
  }

}
