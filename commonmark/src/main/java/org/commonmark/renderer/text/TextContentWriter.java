package org.commonmark.renderer.text;

import java.util.LinkedList;
import org.commonmark.internal.renderer.AppendableWriter;

public class TextContentWriter {

    private final AppendableWriter writer;
    private final LineBreakRendering lineBreakRendering;

    private final LinkedList<String> prefixes = new LinkedList<>();
    private final LinkedList<Boolean> tight = new LinkedList<>();

    private String blockSeparator = null;

    public TextContentWriter(Appendable out) {
        this(out, LineBreakRendering.COMPACT);
    }

    public TextContentWriter(Appendable out, LineBreakRendering lineBreakRendering) {
        this.writer = new AppendableWriter(out);
        this.lineBreakRendering = lineBreakRendering;
    }

    public void whitespace() {
        char lastChar = writer.getLastChar();
        if (lastChar != 0 && lastChar != ' ') {
            write(' ');
        }
    }

    public void colon() {
        char lastChar = writer.getLastChar();
        if (lastChar != 0 && lastChar != ':') {
            write(':');
        }
    }

    public void line() {
        append('\n');
        writePrefixes();
    }

    public void block() {
        blockSeparator =
                lineBreakRendering == LineBreakRendering.STRIP
                        ? " "
                        : //
                        lineBreakRendering == LineBreakRendering.COMPACT || isTight()
                                ? "\n"
                                : "\n\n";
    }

    public void resetBlock() {
        blockSeparator = null;
    }

    public void writeStripped(String s) {
        write(s.replaceAll("[\\r\\n\\s]+", " "));
    }

    public void write(String s) {
        flushBlockSeparator();
        append(s);
    }

    public void write(char c) {
        flushBlockSeparator();
        append(c);
    }

    /**
     * Push a prefix onto the top of the stack. All prefixes are written at the beginning of each
     * line, until the prefix is popped again.
     *
     * @param prefix the raw prefix string
     */
    public void pushPrefix(String prefix) {
        prefixes.addLast(prefix);
    }

    /**
     * Write a prefix.
     *
     * @param prefix the raw prefix string to write
     */
    public void writePrefix(String prefix) {
        write(prefix);
    }

    /** Remove the last prefix from the top of the stack. */
    public void popPrefix() {
        prefixes.removeLast();
    }

    /**
     * Change whether blocks are tight or loose. Loose is the default where blocks are separated by
     * a blank line. Tight is where blocks are not separated by a blank line. Tight blocks are used
     * in lists, if there are no blank lines within the list.
     *
     * <p>Note that changing this does not affect block separators that have already been enqueued
     * with {@link #block()}, only future ones.
     */
    public void pushTight(boolean tight) {
        this.tight.addLast(tight);
    }

    /** Remove the last "tight" setting from the top of the stack. */
    public void popTight() {
        this.tight.removeLast();
    }

    private boolean isTight() {
        return !tight.isEmpty() && tight.getLast();
    }

    private void writePrefixes() {
        for (String prefix : prefixes) {
            append(prefix);
        }
    }

    /**
     * If a block separator has been enqueued with {@link #block()} but not yet written, write it
     * now.
     */
    private void flushBlockSeparator() {
        if (blockSeparator != null) {
            if (blockSeparator.equals("\n") || blockSeparator.equals("\n\n")) {
                for (int i = 0; i < blockSeparator.length(); i++) {
                    var sep = blockSeparator.charAt(i);
                    append(sep);
                    writePrefixes();
                }
            } else {
                append(blockSeparator);
            }
            blockSeparator = null;
        }
    }

    private void append(String s) {
        writer.append(s);
    }

    private void append(char c) {
        writer.append(c);
    }
}
