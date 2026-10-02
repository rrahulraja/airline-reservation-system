package com.airline.booking.airport;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Seeded reference data. No CRUD API exists for airports by design.
 *
 * <p>A class, not a record: Hibernate needs a no-arg constructor and mutable
 * fields to hydrate instances and perform dirty checking.
 */
@Entity
@Table(name = "airport")
public class Airport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "iata_code", nullable = false, length = 3, updatable = false)
  private String iataCode;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(nullable = false, length = 80)
  private String city;

  @Column(nullable = false, length = 80)
  private String country;

  protected Airport() {
  }

  public Long getId() {
    return id;
  }

  public String getIataCode() {
    return iataCode;
  }

  public String getName() {
    return name;
  }

  public String getCity() {
    return city;
  }

  public String getCountry() {
    return country;
  }
}
