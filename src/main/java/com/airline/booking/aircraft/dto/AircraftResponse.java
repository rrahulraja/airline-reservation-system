package com.airline.booking.aircraft.dto;

import com.airline.booking.aircraft.Aircraft;

public record AircraftResponse(String aircraftCode, String aircraftType, int rowCount, String seatLetters, int capacity) {

  public static AircraftResponse from(Aircraft aircraft) {
    return new AircraftResponse(aircraft.getAircraftCode(),
        aircraft.getAircraftType(),
        aircraft.getRowCount(),
        aircraft.getSeatLetters(),
        aircraft.capacity());
  }
}
