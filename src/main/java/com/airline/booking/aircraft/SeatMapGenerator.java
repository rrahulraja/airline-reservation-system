package com.airline.booking.aircraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;


/**
 * Expands an aircraft configuration into seat labels, and validates labels against it.
 *
 * <p>Deliberately pure: no repository, no clock, no Spring dependencies beyond the
 * stereotype. That makes it unit-testable with no context at all, which matters because its correctness underpins both the seat map and every
 * booking.
 */
@Component
public class SeatMapGenerator {

  /**
   * One to three digits for the row, then exactly one uppercase letter.
   */
  private static final Pattern SEAT_LABEL = Pattern.compile("^\\d{1,3}[A-Z]$");

  /**
   * Every seat label for the aircraft, in row-major order:
   * {@code 1A, 1B, ... 1F, 2A, ...}. Row numbers start at 1.
   */
  public List<String> generateLabels(int rowCount, String seatLetters) {
    List<String> labels = new ArrayList<>(rowCount * seatLetters.length());

    for (int row = 1; row <= rowCount; row++) {
      for (char letter : seatLetters.toCharArray()) {
        labels.add(row + String.valueOf(letter));
      }
    }

    return labels;
  }

  public int capacity(int rowCount, String seatLetters) {
    return rowCount * seatLetters.length();
  }


  /**
   * Trims and uppercases a client-supplied label, returning empty when it is not
   * syntactically a seat label.
   *
   * <p>Syntax only — this does not know the aircraft. {@code "99Z"} normalizes
   * successfully and is then rejected by {@link #isValidFor}.
   */
  public Optional<String> normalize(String rawLabel) {
    if (rawLabel == null) {
      return Optional.empty();
    }
    String candidate = rawLabel.trim().toUpperCase();
    if (!SEAT_LABEL.matcher(candidate).matches()) {
      return Optional.empty();
    }
    // Reject row 0 and leading zeros ("01A"), which would otherwise be a second
    // spelling of an existing seat and defeat the unique index.
    if (candidate.charAt(0) == '0') {
      return Optional.empty();
    }
    return Optional.of(candidate);
  }

  /**
   * Whether an already-normalized label names a real seat on this aircraft.
   *
   * @param normalizedLabel output of {@link #normalize}, not raw client input
   */
  public boolean isValidFor(String normalizedLabel, int rowCount, String seatLetters) {
    if (normalizedLabel == null || normalizedLabel.length() < 2) {
      return false;
    }
    char letter = normalizedLabel.charAt(normalizedLabel.length() - 1);
    if (seatLetters.indexOf(letter) < 0) {
      return false;
    }
    int row;
    try {
      row = Integer.parseInt(normalizedLabel.substring(0, normalizedLabel.length() - 1));
    } catch (NumberFormatException e) {
      return false;
    }
    return row >= 1 && row <= rowCount;
  }

}
