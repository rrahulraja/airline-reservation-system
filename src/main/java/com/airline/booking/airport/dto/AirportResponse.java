package com.airline.booking.airport.dto;

import com.airline.booking.airport.Airport;

public record AirportResponse(String iatCode, String name, String city, String country) {

  public static AirportResponse from(Airport airport) {
    return new AirportResponse(airport.getIataCode(),
        airport.getName(),
        airport.getCity(),
        airport.getCountry());
  }

}
