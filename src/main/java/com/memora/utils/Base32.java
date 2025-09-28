package com.memora.utils;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * Parse and format base 32 numbers.
 *
 * Some of this may apply to almost any format but there's much that won't such
 * as allowing both uppercase and lowercase forms of each digit.
 *
 * See: http://en.wikipedia.org/wiki/Base32
 */
public class Base32 {
    // The character sets.
    // Like Hex but up to V

    private static final String base32HexCharacterSet = "0123456789ABCDEFGHIJKLMNOPQRSTUV";
    // Common alternative - avoids O/0, i/1 etc.
    private static final String base32CharacterSet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    // Avoids vowels (and therefore real words)
    private static final String zBase32CharacterSet = "YBNDRFG8EJKMCPQXOT1UWISZA345H769";
    // Avoids o/0 confusion.
    private static final String crockfordCharacterSet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    // Known/published formats.
    // Uses the BigInteger formatter.
    public static final Base32 ordinary = new Base32();
    // A lot like the BigInteger formatter - but using my mechanism.
    public static final Base32 base32Hex = new Base32(base32HexCharacterSet);
    // The RFC 4648 Base32.
    public static final Base32 base32 = new Base32(base32CharacterSet);
    // Supposedly more natural than RFC 4648.
    public static final Base32 zBase32 = new Base32(zBase32CharacterSet);
    // Much like normal but recodes some similar looking characters to the same character.
    public static final Base32 crockfords = new Base32(crockfordCharacterSet, "O0", "o0", "L1", "l1", "I1", "i1");
    // Invalid character.
    private static final int Invalid = -1;
    // The radix - fixed at 32 bits.
    private static final int radix = 32;
    // The bits per digit - could use (int) (Math.log(radix) / Math.log(2))
    private static final int bitsPerDigit = 5;
    // The bits per byte.
    private static final int bitsPerByte = 8;
    // Translation table for each code.
    private final char[] formatTable;
    // Translation table for each character.
    private final int[] parseTable;

    // Constructor - Probably should be private but why restrict the user.
    public Base32() {
        // Empty tables makes us match BigInteger format so no formatting/parsing is required.
        formatTable = null;
        parseTable = null;
    }

    // Constructor with character set and optional extras :).
    protected Base32(String characterSet, String... extras) {
        // Check the character set against the radix.
        if (characterSet.length() != radix) {
            throw new NumberFormatException("Invalid character set. Must be " + radix + " long");
        }
        // Build the format table.
        formatTable = buildFormatTable(characterSet);
        // And the parse table.
        parseTable = buildParseTable(characterSet, extras);
    }

    // Build a format table from the character set.
    private char[] buildFormatTable(String characterSet) {
        // Start clear.
        char[] table = new char[radix];
        // Put each character from the character set in.
        for (int i = 0; i < radix; i++) {
            table[i] = characterSet.charAt(i);
        }
        return table;
    }

    private int[] buildParseTable(String characterSet, String... extras) {
        // Handle all characters up to and including 'z'.
        int[] table = new int['z' + 1];
        // By default invalid character.
        Arrays.fill(table, Invalid);
        // Lowercase and uppercase versions.
        String lc = characterSet.toLowerCase();
        String uc = characterSet.toUpperCase();
        // Walk through the character set.
        for (int i = 0; i < radix; i++) {
            char l = lc.charAt(i);
            char u = uc.charAt(i);
            // Something wrong if we've already filled this one in.
            if (table[l] == Invalid && table[u] == Invalid) {
                // Put both lowercase and uppercase forms in the table.
                table[l] = i;
                table[u] = i;
            } else {
                // Failed.
                throw new NumberFormatException("Invalid character set - duplicate found at position " + i);
            }
        }
        // Add extras.
        for (String pair : extras) {
            // Each Must be length 2.
            if (pair.length() == 2) {
                // From
                int f = pair.charAt(1);
                // To
                int t = pair.charAt(0);
                // Something wrong if we've already filled this one in or we are copying from one that is not filled in.
                if (table[f] != Invalid && table[t] == Invalid) {
                    // EG "O0" means a capital oh should be treated as a zero.
                    table[t] = table[f];
                } else {
                    // Failed.
                    throw new NumberFormatException("Invalid character set extra - copying from " + f + " to " + t);
                }
            } else {
                // Failed.
                throw new NumberFormatException("Invalid extra \"" + pair + "\" - should be 2 characters wide.");
            }

        }
        return table;
    }

    public String format(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        int digit = 0;
        int currByte, nextByte;

        for (int i = 0; i < bytes.length;) {
            currByte = bytes[i] & 0xFF; // Unsigned

            if (index > 3) {
                if ((i + 1) < bytes.length) {
                    nextByte = bytes[i + 1] & 0xFF;
                } else {
                    nextByte = 0;
                }
                digit = currByte & (0xFF >> index);
                digit <<= (index + 5 - 8);
                digit |= nextByte >> (8 - (index + 5 - 8));
                index = (index + 5) % 8;
                if (index == 0) i++;
            } else {
                digit = (currByte >> (8 - (index + 5))) & 0x1F;
                index = (index + 5) % 8;
                if (index == 0) i++;
            }

            sb.append(formatTable[digit]);
        }

        return sb.toString();
    }

}
