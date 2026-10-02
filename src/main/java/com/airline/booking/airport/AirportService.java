package com.airline.booking.airport;

import com.airline.booking.common.exceptions.AirportNotFoundException;
import com.airline.booking.common.exceptions.ValidationException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only access to seeded airports, plus the one piece of real logic here:
 * turning client-supplied IATA input into an {@link Airport}.
 */
@Service
@Transactional(readOnly = true)
public class AirportService {

  private static final Pattern IATA = Pattern.compile("^[A-Z]{3}$");

  private final AirportRepository repository;

  public AirportService(AirportRepository repository) {
    this.repository = repository;
  }

  public List<Airport> findAll() {
    return repository.findAll();
  }

  /**
   * Resolves an IATA code, accepting any casing and surrounding whitespace.
   *
   * @throws ValidationException       when the input is not three letters (400)
   * @throws AirportNotFoundException  when it is well-formed but unknown (404)
   */
  public Airport resolveByIata(String rawCode) {
    String code = rawCode == null ? "" : rawCode.trim().toUpperCase();

    if (!IATA.matcher(code).matches()) {
      throw new ValidationException(
          "IATA code must be exactly three letters, got: " + rawCode,
          List.of("iataCode: must match [A-Z]{3}"));
    }

    return repository.findByIataCode(code)
        .orElseThrow(() -> new AirportNotFoundException(code));
  }




}
