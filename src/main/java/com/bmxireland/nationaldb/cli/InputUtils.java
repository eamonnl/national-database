package com.bmxireland.nationaldb.cli;

/**
 * Utility methods for processing user input from the CLI.
 */
public class InputUtils {

    private InputUtils() {}

    /**
     * Normalises a file path typed or drag-and-dropped into the terminal.
     *
     * On macOS, dragging a file into Terminal shell-escapes special characters
     * (spaces, parentheses, ampersands, etc.) with a preceding backslash, e.g.:
     *   /Users/foo/My\ File\ \(2026\).xlsx
     * Java's File treats those backslashes as literals, so the path lookup fails.
     * This method removes shell-escape backslashes so the path resolves correctly.
     */
    public static String normalizeFilePath(String raw) {
        if (raw == null) return null;
        // Replace any backslash followed by any character with just that character.
        // This undoes shell escaping while leaving Windows-style path separators intact
        // (a bare backslash at end-of-string is left as-is).
        return raw.replaceAll("\\\\(.)", "$1").trim();
    }
}
