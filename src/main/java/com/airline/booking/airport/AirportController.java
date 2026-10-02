package com.airline.booking.airport;

import com.airline.booking.airport.dto.AirportResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/airports")
public class AirportController {

  private final AirportService service;

  public AirportController(AirportService service) {
    this.service = service;
  }

  @GetMapping
  public List<AirportResponse> list() {
    return service.findAll().stream().map(AirportResponse::from).toList();
  }

}
