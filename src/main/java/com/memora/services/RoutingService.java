package com.memora.services;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class RoutingService {


    private RoutingService() {
    }

    /**
     * Jump Consistent Hash (Lamping & Veach). Maps 64-bit key -> bucket in [0,
     * numBuckets).
     */
    private static int jumpConsistentHash(long key, int numBuckets) {
        if (numBuckets <= 0)
            throw new IllegalArgumentException("numBuckets must be greater than 0");
        long b = -1, j = 0;
        while (j < numBuckets) {
            b = j;
            key = key * 2862933555777941757L + 1;
            j = (long) ((b + 1) * ((1L << 31) / ((double) ((key >>> 33) + 1))));
        }
        return (int) b;
    }

    /**
     * Convenience method: hashes a String using MurmurHash3 (x64_128) and maps to a bucket.
     */
    private static int jumpConsistentHash(String key, int numBuckets) {
        long hash64 = murmurHash3_x64_128(key.getBytes(StandardCharsets.UTF_8), 0);
        return jumpConsistentHash(hash64, numBuckets);
    }

    /**
     * MurmurHash3 x64_128 implementation (returns lower 64 bits).
     * @param data input bytes
     * @param seed optional seed (can be 0 for default)
     */
    private static long murmurHash3_x64_128(byte[] data, int seed) {
        final int length = data.length;
        final int nblocks = length >>> 4; // / 16

        long h1 = seed & 0xFFFFFFFFL;
        long h2 = seed & 0xFFFFFFFFL;

        final long c1 = 0x87c37b91114253d5L;
        final long c2 = 0x4cf5ad432745937fL;

        // Body
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < nblocks; i++) {
            long k1 = buffer.getLong();
            long k2 = buffer.getLong();

            k1 *= c1;
            k1 = Long.rotateLeft(k1, 31);
            k1 *= c2;
            h1 ^= k1;

            h1 = Long.rotateLeft(h1, 27);
            h1 += h2;
            h1 = h1 * 5 + 0x52dce729;

            k2 *= c2;
            k2 = Long.rotateLeft(k2, 33);
            k2 *= c1;
            h2 ^= k2;

            h2 = Long.rotateLeft(h2, 31);
            h2 += h1;
            h2 = h2 * 5 + 0x38495ab5;
        }

        // Tail
        long k1 = 0;
        long k2 = 0;
        int tailStart = nblocks << 4;
        switch (length & 15) {
            case 15: k2 ^= ((long) data[tailStart + 14] & 0xffL) << 48;
            case 14: k2 ^= ((long) data[tailStart + 13] & 0xffL) << 40;
            case 13: k2 ^= ((long) data[tailStart + 12] & 0xffL) << 32;
            case 12: k2 ^= ((long) data[tailStart + 11] & 0xffL) << 24;
            case 11: k2 ^= ((long) data[tailStart + 10] & 0xffL) << 16;
            case 10: k2 ^= ((long) data[tailStart + 9] & 0xffL) << 8;
            case 9:  k2 ^= ((long) data[tailStart + 8] & 0xffL);
                k2 *= c2;
                k2 = Long.rotateLeft(k2, 33);
                k2 *= c1;
                h2 ^= k2;
            case 8:  k1 ^= ((long) data[tailStart + 7] & 0xffL) << 56;
            case 7:  k1 ^= ((long) data[tailStart + 6] & 0xffL) << 48;
            case 6:  k1 ^= ((long) data[tailStart + 5] & 0xffL) << 40;
            case 5:  k1 ^= ((long) data[tailStart + 4] & 0xffL) << 32;
            case 4:  k1 ^= ((long) data[tailStart + 3] & 0xffL) << 24;
            case 3:  k1 ^= ((long) data[tailStart + 2] & 0xffL) << 16;
            case 2:  k1 ^= ((long) data[tailStart + 1] & 0xffL) << 8;
            case 1:  k1 ^= ((long) data[tailStart] & 0xffL);
                k1 *= c1;
                k1 = Long.rotateLeft(k1, 31);
                k1 *= c2;
                h1 ^= k1;
        }

        // Finalization
        h1 ^= length;
        h2 ^= length;

        h1 += h2;
        h2 += h1;

        h1 = fmix64(h1);
        h2 = fmix64(h2);

        h1 += h2;
        // h2 += h1; // not needed if we just want lower 64 bits

        return h1;
    }

    private static long fmix64(long k) {
        k ^= k >>> 33;
        k *= 0xff51afd7ed558ccdL;
        k ^= k >>> 33;
        k *= 0xc4ceb9fe1a85ec53L;
        k ^= k >>> 33;
        return k;
    }

    public static int getBucketIndex(String key, int numBuckets) {
        return jumpConsistentHash(key, numBuckets);
    }
}
