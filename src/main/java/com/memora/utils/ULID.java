package com.memora.utils;

import java.security.SecureRandom;
import java.time.Instant;

public final class ULID {
    private static final SecureRandom secureRandom = new SecureRandom();
    private static final int TIMESTAMP_BITS = 40;
    private static final int RANDOM_BITS = 24;

    // Masks for bit lengths
    private static final long TIMESTAMP_MASK = (1L << TIMESTAMP_BITS) - 1;
    private static final long RANDOM_MASK = (1L << RANDOM_BITS) - 1;

    public static String generate() {
        long timestampMillis = Instant.now().toEpochMilli() & TIMESTAMP_MASK;
        long randomValue = secureRandom.nextInt(1 << RANDOM_BITS) & RANDOM_MASK;

        long combined = (timestampMillis << RANDOM_BITS) | randomValue;

        return encodeBase32(combined, TIMESTAMP_BITS + RANDOM_BITS);
    }

    private static String encodeBase32(long value, int totalBits) {
        byte[] bytes = longToBytes(value, (totalBits + 7) / 8);
        return Base32.crockfords.format(bytes);
    }

    private static byte[] longToBytes(long value, int byteCount) {
        byte[] bytes = new byte[byteCount];
        for (int i = byteCount - 1; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }
}
