package com.airline.booking.aircraft;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftRepository extends JpaRepository<Aircraft, Long> {

  Optional<Aircraft> findByAircraftCode(String aircraftCode);

}
