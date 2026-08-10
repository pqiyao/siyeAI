package com.example.sillyspringboot.integration.sillytavern;

import java.util.Locale;

/** Validates that ST keeps character files inside the server-assigned namespace. */
public final class StCharacterFileNamePolicy {

    private StCharacterFileNamePolicy() {
    }

    public static boolean isExpectedImportResult(String returnedFileName, String preservedName) {
        String returned = normalize(returnedFileName);
        String expected = normalize(preservedName);
        if (!isSimplePngFileName(returned) || expected.isBlank()) {
            return false;
        }
        String expectedStem = stripPngSuffix(expected);
        if (expectedStem.isBlank()) {
            return false;
        }
        return returned.toLowerCase(Locale.ROOT).startsWith(expectedStem.toLowerCase(Locale.ROOT));
    }

    public static boolean isStableSyncResult(String expectedFileName, String returnedFileName) {
        String expected = normalize(expectedFileName);
        String returned = normalize(returnedFileName);
        return isSimplePngFileName(expected)
                && isSimplePngFileName(returned)
                && expected.equalsIgnoreCase(returned);
    }

    public static boolean isSimplePngFileName(String value) {
        String fileName = normalize(value);
        return !fileName.isBlank()
                && fileName.indexOf('/') < 0
                && fileName.indexOf('\\') < 0
                && !fileName.contains("..")
                && fileName.toLowerCase(Locale.ROOT).endsWith(".png");
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripPngSuffix(String value) {
        return value.toLowerCase(Locale.ROOT).endsWith(".png")
                ? value.substring(0, value.length() - 4)
                : value;
    }
}
