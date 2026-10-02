package com.airline.booking.aircraft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

public class SeatMapGeneratorTest {

  private final SeatMapGenerator generator = new SeatMapGenerator();

  @Test
  @DisplayName("labels are generated row-major, rows starting at 1")
  void generateLabelsRowMajorOrder() {
    assertThat(generator.generateLabels(2, "ABC"))
        .containsExactly("1A", "1B", "1C", "2A", "2B", "2C");
  }

  @Test
  @DisplayName("a full A320 yields 180 labels from 1A to 30F")
  void generateLabelsFullA320() {
    var labels = generator.generateLabels(30, "ABCDEF");

    assertThat(labels).hasSize(180);
    assertThat(labels.get(0)).isEqualTo("1A");
    assertThat(labels.get(labels.size() - 1)).isEqualTo("30F");
  }

  @Test
  @DisplayName("capacity is rows times letters")
  void capacity() {
    assertThat(generator.capacity(30, "ABCDEF")).isEqualTo(180);
    assertThat(generator.capacity(18, "ABCD")).isEqualTo(72);
    assertThat(generator.capacity(1, "A")).isEqualTo(1);
  }

  @Test
  @DisplayName("normalize trims whitespace and uppercases")
  void normalizeTrimsAndUppercases() {
    assertThat(generator.normalize(" 12a ")).contains("12A");
    assertThat(generator.normalize("12a")).contains("12A");
    assertThat(generator.normalize("12A")).contains("12A");
  }

  @Test
  @DisplayName("normalize rejects anything that is not a seat label")
  void normalizeRejectsGarbage() {
    assertThat(generator.normalize(null)).isEmpty();
    assertThat(generator.normalize("")).isEmpty();
    assertThat(generator.normalize("A12")).isEmpty();
    assertThat(generator.normalize("12")).isEmpty();
    assertThat(generator.normalize("12AB")).isEmpty();
    assertThat(generator.normalize("1 2A")).isEmpty();
    assertThat(generator.normalize("12-A")).isEmpty();
  }

  @Test
  @DisplayName("normalize rejects leading zeros, which would be a second spelling of one seat")
  void normalizeRejectsLeadingZero() {
    assertThat(generator.normalize("01A")).isEmpty();
    assertThat(generator.normalize("0A")).isEmpty();
  }

  @Test
  @DisplayName("isValidFor rejects a row beyond the aircraft")
  void isValidForRowOutOfRange() {
    assertThat(generator.isValidFor("31A", 30, "ABCDEF")).isFalse();
    assertThat(generator.isValidFor("100A", 30, "ABCDEF")).isFalse();
  }

  @Test
  @DisplayName("isValidFor rejects a letter the aircraft does not have")
  void isValidForLetterNotInConfig() {
    assertThat(generator.isValidFor("1G", 30, "ABCDEF")).isFalse();
    assertThat(generator.isValidFor("1D", 30, "ABC")).isFalse();
  }

  @Test
  @DisplayName("isValidFor accepts the first and last real seat")
  void isValidForAcceptsBoundaries() {
    assertThat(generator.isValidFor("1A", 30, "ABCDEF")).isTrue();
    assertThat(generator.isValidFor("30F", 30, "ABCDEF")).isTrue();
    assertThat(generator.isValidFor("18D", 18, "ABCD")).isTrue();
  }

  @Test
  @DisplayName("every generated label validates against the config that produced it")
  void generatedLabelsAreAllValid() {
    var labels = generator.generateLabels(18, "ABCD");

    assertThat(labels).allMatch(label -> generator.isValidFor(label, 18, "ABCD"));
  }


}
