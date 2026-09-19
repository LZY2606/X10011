package org.commonmark.internal.util;

import java.io.IOException;

/**
 * Wraps an {@link Appendable} to track the last character that was written. Any {@link IOException}
 * from the underlying appendable is rethrown as a {@link RuntimeException}.
 */
public class LastCharAppendable {

    private final Appendable out;
    private char lastChar;

    public LastCharAppendable(Appendable out) {
        this.out = out;
    }

    /**
     * @return the last character that was written, or 0 if nothing was written yet
     */
    public char getLastChar() {
        return lastChar;
    }

    /**
     * Set the last written character. Useful if what was appended does not correspond 1:1 to the
     * logical content (e.g. escaping), so that the last logical character can be recorded.
     */
    public void setLastChar(char lastChar) {
        this.lastChar = lastChar;
    }

    /**
     * Append a string. If the string is not empty, the last written character is updated to its
     * last character; appending an empty string leaves it unchanged.
     */
    public void append(String s) {
        try {
            out.append(s);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int length = s.length();
        if (length != 0) {
            lastChar = s.charAt(length - 1);
        }
    }

    /** Append a single character and update the last written character. */
    public void append(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        lastChar = c;
    }
}
