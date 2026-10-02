package com.airline.booking.airport;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportRepository extends JpaRepository<Airport, Long> {

  Optional<Airport> findByIataCode(String iataCode);

}
