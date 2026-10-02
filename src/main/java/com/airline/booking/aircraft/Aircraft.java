package com.airline.booking.aircraft;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Seeded reference data describing a fixed seat layout.
 *
 * <p>The seat map is NOT stored as rows. {@code rowCount} and {@code seatLetters}
 * fully describe every seat on the aircraft, and {@code SeatMapGenerator} expands
 * them on demand. For 300 schedules over a year that is the difference between
 * zero rows and roughly 18 million.
 */
@Entity
@Table(name = "aircraft")
public class Aircraft {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "aircraft_code", nullable = false, length = 16, updatable = false)
  private String aircraftCode;

  @Column(name = "aircraft_type", nullable = false, length = 40)
  private String aircraftType;

  /** Number of seat rows, 1-based. */
  @Column(name = "row_count", nullable = false)
  private int rowCount;

  /** Seat letters in cabin order, e.g. "ABCDEF". */
  @Column(name = "seat_letters", nullable = false, length = 12)
  private String seatLetters;

  protected Aircraft() {
  }

  public Long getId() {
    return id;
  }

  public String getAircraftCode() {
    return aircraftCode;
  }

  public String getAircraftType() {
    return aircraftType;
  }

  public int getRowCount() {
    return rowCount;
  }

  public String getSeatLetters() {
    return seatLetters;
  }

  /** Total seats on this aircraft. Derived, never stored. */
  public int capacity() {
    return rowCount * seatLetters.length();
  }

}
