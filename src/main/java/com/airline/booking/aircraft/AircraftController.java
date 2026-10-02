package com.airline.booking.aircraft;

import com.airline.booking.aircraft.dto.AircraftResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only; */

@RestController
@RequestMapping("/api/aircraft")
public class AircraftController {

  private final AircraftRepository repository;

  public AircraftController(AircraftRepository repository) {
    this.repository = repository;
  }

  @GetMapping
  public List<AircraftResponse> list() {
    return repository.findAll().stream().map(AircraftResponse::from).toList();
  }

}
