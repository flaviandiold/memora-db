package com.memora.utils;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * A utility class to encode and decode data in various Base32 formats.
 *
 * <p>This class provides implementations for several common Base32 alphabets,
 * including the standard RFC 4648, zBase32, and Crockford's Base32.
 * It supports both encoding (byte array to String) and decoding (String to byte array).
 *
 * <p>See: http://en.wikipedia.org/wiki/Base32
 */
public class Base32 {

    // The character sets for various Base32 implementations.
    private static final String base32HexCharacterSet = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
    private static final String base32CharacterSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String zBase32CharacterSet = "YBNDRFG8EJKMCPQXOT1UWISZA345H769";
    private static final String crockfordCharacterSet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    // Publicly accessible instances for common Base32 formats.
    public static final Base32 base32Hex = new Base32(base32HexCharacterSet);
    public static final Base32 base32 = new Base32(base32CharacterSet);
    public static final Base32 zBase32 = new Base32(zBase32CharacterSet);
    public static final Base32 crockfords = new Base32(crockfordCharacterSet, "O0", "o0", "L1", "l1", "I1", "i1");

    // Constants
    private static final int Invalid = -1;
    private static final int radix = 32;
    private static final char paddingChar = '=';

    // Internal lookup tables for encoding and decoding.
    private final char[] formatTable;
    private final int[] parseTable;

    /**
     * Private constructor for the "ordinary" instance which is not used.
     */
    private Base32() {
        this.formatTable = null;
        this.parseTable = null;
    }

    /**
     * Creates a new Base32 instance with a specified character set and optional character mappings.
     *
     * @param characterSet A 32-character string representing the encoding alphabet.
     * @param extras       Optional pairs of characters for aliasing during parsing (e.g., "O0" to treat 'O' as '0').
     */
    protected Base32(String characterSet, String... extras) {
        if (characterSet.length() != radix) {
            throw new IllegalArgumentException("Invalid character set. Must be " + radix + " characters long.");
        }
        this.formatTable = characterSet.toCharArray();
        this.parseTable = buildParseTable(characterSet, extras);
    }

    /**
     * Builds the lookup table used for decoding characters into their 5-bit integer values.
     */
    private int[] buildParseTable(String characterSet, String... extras) {
        int[] table = new int['z' + 1];
        Arrays.fill(table, Invalid);

        for (int i = 0; i < characterSet.length(); i++) {
            char upper = Character.toUpperCase(characterSet.charAt(i));
            char lower = Character.toLowerCase(characterSet.charAt(i));
            if (table[upper] != Invalid || table[lower] != Invalid) {
                throw new IllegalArgumentException("Invalid character set - duplicate character found at position " + i);
            }
            table[upper] = i;
            table[lower] = i;
        }

        for (String pair : extras) {
            if (pair.length() != 2) {
                throw new IllegalArgumentException("Invalid extra mapping \"" + pair + "\" - must be 2 characters wide.");
            }
            char to = pair.charAt(0);
            char from = pair.charAt(1);

            if (table[from] == Invalid) {
                throw new IllegalArgumentException("Invalid extra mapping - cannot map from a character that is not in the alphabet: " + from);
            }
            table[to] = table[from];
        }
        return table;
    }

    /**
     * Encodes a byte array into a Base32 string. This method replaces the buggy 'format' method.
     *
     * @param data The byte array to encode.
     * @return The Base32-encoded string, with padding.
     */
    public String encode(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;

            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                int index = (buffer >> bitsLeft) & 0x1F;
                result.append(formatTable[index]);
            }
        }

        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(formatTable[index]);
        }

        while (result.length() % 8 != 0) {
            result.append(paddingChar);
        }

        return result.toString();
    }

    /**
     * Decodes a Base32 string into a byte array.
     *
     * @param base32String The Base32 string to decode.
     * @return The decoded byte array.
     */
    public byte[] decode(String base32String) {
        if (base32String == null || base32String.isEmpty()) {
            return new byte[0];
        }

        int end = base32String.length();
        while (end > 0 && base32String.charAt(end - 1) == paddingChar) {
            end--;
        }
        String unpadded = base32String.substring(0, end).replaceAll("\\s", "");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;

        for (char c : unpadded.toCharArray()) {
            if (c >= parseTable.length || parseTable[c] == Invalid) {
                throw new IllegalArgumentException("Invalid character in Base32 string: '" + c + "'");
            }

            buffer = (buffer << 5) | (parseTable[c] & 0x1F);
            bitsLeft += 5;

            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.write((buffer >> bitsLeft) & 0xFF);
            }
        }

        return out.toByteArray();
    }
}