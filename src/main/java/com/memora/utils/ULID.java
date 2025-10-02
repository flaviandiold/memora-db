package com.memora.utils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;

/**
 * A high-performance, thread-safe, and specification-compliant generator for
 * Universally Unique Lexicographically Sortable Identifiers (ULIDs).
 *
 * <p>This generator ensures monotonicity by incrementing the random part of the
 * ULID if multiple ULIDs are generated within the same millisecond. It is designed
 * for use in multi-threaded environments.
 *
 * <p>A ULID is a 128-bit value, composed of:
 * <ul>
 * <li>A 48-bit timestamp (milliseconds since the Unix epoch).</li>
 * <li>An 80-bit cryptographically secure random value.</li>
 * </ul>
 * The final output is a 26-character string encoded using Crockford's Base32 alphabet.
 */
public final class ULID {

    private static final int ULID_STRING_LENGTH = 26;
    private static final int ULID_BYTE_LENGTH = 16;
    private static final int TIMESTAMP_BYTE_LENGTH = 6;
    private static final int RANDOM_BYTE_LENGTH = 10;
    private static final long TIMESTAMP_MASK = 0x0000FFFFFFFFFFFFL;

    // A shared generator instance to ensure monotonicity across the application.
    private static final Generator GENERATOR = new Generator();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ULID() {
    }

    /**
     * Generates a new, unique, and monotonic ULID string.
     *
     * @return A new 26-character ULID string.
     */
    public static String generate() {
        return GENERATOR.generate();
    }

    /**
     * An inner class that encapsulates the state and logic for ULID generation,
     * ensuring thread safety and monotonicity.
     */
    private static final class Generator {

        private static final SecureRandom RANDOM = new SecureRandom();
        private final byte[] lastRandomBytes = new byte[RANDOM_BYTE_LENGTH];
        private long lastTimestamp = 0L;

        /**
         * The core generation logic, synchronized to ensure thread safety.
         * @return A new ULID string.
         */
        synchronized String generate() {
            long currentTimestamp = Instant.now().toEpochMilli() & TIMESTAMP_MASK;

            if (currentTimestamp > this.lastTimestamp) {
                // Time has moved forward, generate new random bytes.
                RANDOM.nextBytes(this.lastRandomBytes);
                this.lastTimestamp = currentTimestamp;
            } else if (currentTimestamp == this.lastTimestamp) {
                // Same millisecond, increment the random part.
                incrementRandom();
            } else {
                // Clock has moved backward, which violates monotonicity.
                // The ULID spec recommends throwing an error.
                throw new IllegalStateException("Clock moved backwards. Cannot generate monotonic ULID.");
            }

            return encode(this.lastTimestamp, this.lastRandomBytes);
        }

        /**
         * Increments the 80-bit (10-byte) random value by one.
         * This is used to ensure monotonicity within the same millisecond.
         */
        private void incrementRandom() {
            for (int i = lastRandomBytes.length - 1; i >= 0; i--) {
                lastRandomBytes[i]++;
                if (lastRandomBytes[i] != 0) {
                    // No carry-over, so we are done.
                    break;
                }
                // If the byte overflowed (became 0), the loop continues to the next byte.
            }
        }
        
        /**
         * Combines the timestamp and random bytes and encodes them into a ULID string.
         * @param timestamp The 48-bit timestamp.
         * @param random The 80-bit random bytes.
         * @return The final, 26-character ULID string.
         */
        private String encode(long timestamp, byte[] random) {
            Objects.requireNonNull(random, "random bytes must not be null");
            if (random.length != RANDOM_BYTE_LENGTH) {
                throw new IllegalArgumentException("random bytes must be 10 bytes long");
            }

            byte[] ulidBytes = new byte[ULID_BYTE_LENGTH];

            // Copy timestamp bytes
            ulidBytes[0] = (byte) (timestamp >> 40);
            ulidBytes[1] = (byte) (timestamp >> 32);
            ulidBytes[2] = (byte) (timestamp >> 24);
            ulidBytes[3] = (byte) (timestamp >> 16);
            ulidBytes[4] = (byte) (timestamp >> 8);
            ulidBytes[5] = (byte) timestamp;

            // Copy random bytes
            System.arraycopy(random, 0, ulidBytes, TIMESTAMP_BYTE_LENGTH, RANDOM_BYTE_LENGTH);

            // Encode using the provided Base32 utility
            String encoded = Base32.crockfords.encode(ulidBytes);
            
            // Trim padding to meet the 26-character spec
            return encoded.substring(0, ULID_STRING_LENGTH);
        }
    }
}