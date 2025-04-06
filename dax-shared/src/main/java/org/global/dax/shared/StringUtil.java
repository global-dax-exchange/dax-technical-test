package org.global.dax.shared;

import java.nio.charset.StandardCharsets;

public final class StringUtil {

    public final static int FIXED_KEY_BYTES = 4;
    public final static int MAX_VALUE_BYTES = 2096;

    private StringUtil() {}

    public static String limitKey(String key) {
        if (key == null) {
            return null;
        }
        return fix(key, FIXED_KEY_BYTES);
    }

    public static String fix(String input, int fixedByteLength) {
        if (input == null || fixedByteLength < 1) {
            throw new IllegalArgumentException("Input cannot be null and fixedByteLength must be at least 1.");
        }

        // Encode the string to bytes using UTF-8
        byte[] encodedBytes = input.getBytes(StandardCharsets.UTF_8);

        if (encodedBytes.length > fixedByteLength) {
            // If the encoded length exceeds the fixed byte length, truncate it
            return truncateStringToBytes(input, fixedByteLength);
        } else if (encodedBytes.length < fixedByteLength) {
            // If the encoded length is less, pad the string
            return padStringToBytes(input, fixedByteLength);
        }

        // If it matches exactly, return the original string
        return input;
    }

    public static String limitValue(String value) {
        return limit(value, MAX_VALUE_BYTES);
    }

    public static String limit(String input, int limitSize) {
        if (input == null || limitSize < 1) {
            throw new IllegalArgumentException("Input cannot be null and fixedSize must be at least 1.");
        }

        // If the input byte array is longer than the fixed size, truncate it
        if (input.getBytes(StandardCharsets.UTF_8).length > limitSize) {
            return truncateStringToBytes(input, limitSize);
        }

        // If already matching, return the original array
        return input;
    }


    private static String truncateStringToBytes(String input, int fixedByteLength) {
        StringBuilder trimmedString = new StringBuilder();
        int byteCount = 0;

        for (char c : input.toCharArray()) {
            byte[] charBytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
            if (byteCount + charBytes.length > fixedByteLength) {
                break; // Stop if adding this character would exceed the limit
            }
            trimmedString.append(c);
            byteCount += charBytes.length; // Increase the byte count
        }

        return trimmedString.toString(); // Return the trimmed string
    }

    private static String padStringToBytes(String input, int fixedByteLength) {
        StringBuilder paddedString = new StringBuilder(input);
        int byteCount = input.getBytes(StandardCharsets.UTF_8).length;

        // Pad with spaces or any desired character until the byte length matches
        while (byteCount < fixedByteLength) {
            paddedString.append(" "); // Using space as a padding character
            byteCount = paddedString.toString().getBytes(StandardCharsets.UTF_8).length;
        }

        return paddedString.toString(); // Return the padded string
    }

}
