package org.commonmark.internal.util;

public class Parsing {
    public static int CODE_BLOCK_INDENT = 4;

    /**
     * Check whether the indentation of the current line is enough for an indented code block,
     * meaning other blocks have to yield to it.
     *
     * @param indent the indentation in columns
     * @return true if the indentation is at least {@link #CODE_BLOCK_INDENT}
     */
    public static boolean isCodeBlockIndent(int indent) {
        return indent >= CODE_BLOCK_INDENT;
    }

    public static int columnsToNextTabStop(int column) {
        // Tab stop is 4
        return 4 - (column % 4);
    }
}
