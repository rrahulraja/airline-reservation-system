package com.airline.booking.common;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates 6-character booking references.
 *
 * <p>The alphabet excludes I, O, 0 and 1 because a PNR gets read aloud, written on paper
 * and typed back in. 32 symbols over 6 characters is about 1.07 billion combinations;
 * uniqueness is still enforced by the unique index on booking.pnr, and the service retries
 * on the rare collision.
 *
 * <p>SecureRandom, not Random: a PNR is a bearer identifier, so predictable values would
 * let someone enumerate other people's bookings.
 */
@Component
public class PnrGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 6;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder pnr = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            pnr.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return pnr.toString();
    }
}
