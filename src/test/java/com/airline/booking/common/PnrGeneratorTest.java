package com.airline.booking.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PnrGeneratorTest {

    private final PnrGenerator generator = new PnrGenerator();

    @Test
    @DisplayName("every PNR is exactly six characters")
    void length() {
        for (int i = 0; i < 10_000; i++) {
            assertThat(generator.generate()).hasSize(6);
        }
    }

    @Test
    @DisplayName("the alphabet excludes visually ambiguous characters")
    void alphabetExcludesAmbiguousCharacters() {
        for (int i = 0; i < 10_000; i++) {
            assertThat(generator.generate())
                    .doesNotContain("I").doesNotContain("O")
                    .doesNotContain("0").doesNotContain("1");
        }
    }

    @Test
    @DisplayName("PNRs are effectively unique at volume")
    void effectivelyUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            seen.add(generator.generate());
        }
        // A seeded or badly reused Random would collide far more than this.
        assertThat(seen).hasSizeGreaterThan(9_990);
    }
}
