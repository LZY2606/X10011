package org.commonmark.internal.renderer;

import java.io.IOException;

/**
 * Wraps an {@link Appendable} and keeps track of the last character that was written. {@link
 * IOException}s are wrapped in {@link RuntimeException}s.
 */
public class AppendableWriter {

    private final Appendable out;
    private char lastChar;

    public AppendableWriter(Appendable out) {
        this.out = out;
    }

    /**
     * @return the last character that was written, or 0 if nothing was written yet
     */
    public char getLastChar() {
        return lastChar;
    }

    /**
     * Set the last character. Note that appending already updates it; this is only needed for cases
     * where the last character of the written output is not the character that should be remembered
     * (e.g. escaped output).
     *
     * @param lastChar the character to remember as the last one
     */
    public void setLastChar(char lastChar) {
        this.lastChar = lastChar;
    }

    /**
     * Append the string and remember its last character. Appending an empty string does not change
     * the last character.
     *
     * @param s the string to append
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

    /**
     * Append the character and remember it as the last character.
     *
     * @param c the character to append
     */
    public void append(char c) {
        try {
            out.append(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        lastChar = c;
    }
}
