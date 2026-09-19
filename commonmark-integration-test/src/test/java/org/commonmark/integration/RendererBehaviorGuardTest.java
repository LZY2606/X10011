package org.commonmark.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.commonmark.node.Document;
import org.commonmark.node.Node;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.html.HtmlWriter;
import org.commonmark.renderer.markdown.MarkdownNodeRendererContext;
import org.commonmark.renderer.markdown.MarkdownNodeRendererFactory;
import org.commonmark.renderer.markdown.MarkdownRenderer;
import org.commonmark.renderer.markdown.MarkdownWriter;
import org.commonmark.renderer.text.TextContentRenderer;
import org.commonmark.renderer.text.TextContentWriter;
import org.junit.jupiter.api.Test;

/**
 * Guard tests that pin observable behavior of the three renderers (HTML, Markdown, text content)
 * that is not covered by the existing test suite. These exist so that internal refactorings can not
 * accidentally flatten behavioral differences between the renderers.
 */
public class RendererBehaviorGuardTest {

    private static final Parser PARSER = Parser.builder().build();

    // NodeRenderer#beforeRoot / NodeRenderer#afterRoot extension points

    private static class RootHookRecordingNodeRenderer implements NodeRenderer {
        private final List<String> events;

        RootHookRecordingNodeRenderer(List<String> events) {
            this.events = events;
        }

        @Override
        public Set<Class<? extends Node>> getNodeTypes() {
            return Set.of(Document.class);
        }

        @Override
        public void render(Node node) {
            events.add("render");
        }

        @Override
        public void beforeRoot(Node rootNode) {
            events.add("beforeRoot");
        }

        @Override
        public void afterRoot(Node rootNode) {
            events.add("afterRoot");
        }
    }

    private static NodeRenderer documentRenderer(List<String> events) {
        return new RootHookRecordingNodeRenderer(events);
    }

    private static MarkdownNodeRendererFactory markdownFactory(NodeRenderer nodeRenderer) {
        return new MarkdownNodeRendererFactory() {
            @Override
            public NodeRenderer create(MarkdownNodeRendererContext context) {
                return nodeRenderer;
            }

            @Override
            public Set<Character> getSpecialCharacters() {
                return Set.of();
            }
        };
    }

    @Test
    public void htmlRendererCallsBeforeAndAfterRootAroundRendering() {
        List<String> events = new ArrayList<>();
        HtmlRenderer renderer =
                HtmlRenderer.builder()
                        .nodeRendererFactory(context -> documentRenderer(events))
                        .build();
        renderer.render(PARSER.parse("hi"));
        assertEquals(List.of("beforeRoot", "render", "afterRoot"), events);
    }

    @Test
    public void markdownRendererDoesNotCallBeforeOrAfterRoot() {
        List<String> events = new ArrayList<>();
        MarkdownRenderer renderer =
                MarkdownRenderer.builder()
                        .nodeRendererFactory(markdownFactory(documentRenderer(events)))
                        .build();
        renderer.render(PARSER.parse("hi"));
        assertEquals(List.of("render"), events);
    }

    @Test
    public void textContentRendererDoesNotCallBeforeOrAfterRoot() {
        List<String> events = new ArrayList<>();
        TextContentRenderer renderer =
                TextContentRenderer.builder()
                        .nodeRendererFactory(context -> documentRenderer(events))
                        .build();
        renderer.render(PARSER.parse("hi"));
        assertEquals(List.of("render"), events);
    }

    // AttributeProvider call count, tag names and provider ordering

    private static AttributeProvider recordingProvider(List<String> calls, String prefix) {
        return (node, tagName, attributes) -> calls.add(prefix + tagName);
    }

    private static HtmlRenderer rendererWithProviders(List<String> calls, String... prefixes) {
        HtmlRenderer.Builder builder = HtmlRenderer.builder();
        for (String prefix : prefixes) {
            builder.attributeProviderFactory(context -> recordingProvider(calls, prefix));
        }
        return builder.build();
    }

    @Test
    public void attributeProviderCalledForPreAndCodeOfFencedCodeBlock() {
        List<String> calls = new ArrayList<>();
        rendererWithProviders(calls, "").render(PARSER.parse("```java\nfoo\n```\n"));
        assertEquals(List.of("pre", "code"), calls);
    }

    @Test
    public void attributeProviderCalledForPAndImgOfImage() {
        List<String> calls = new ArrayList<>();
        rendererWithProviders(calls, "").render(PARSER.parse("![foo](/url)\n"));
        assertEquals(List.of("p", "img"), calls);
    }

    @Test
    public void attributeProviderNotCalledForOmittedSingleParagraphP() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer.Builder builder = HtmlRenderer.builder().omitSingleParagraphP(true);
        builder.attributeProviderFactory(context -> recordingProvider(calls, ""));
        String html = builder.build().render(PARSER.parse("foo\n"));
        assertEquals("foo", html);
        assertEquals(List.of(), calls);
    }

    @Test
    public void attributeProviderCalledForWrappingPOfEscapedHtmlBlock() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer.Builder builder = HtmlRenderer.builder().escapeHtml(true);
        builder.attributeProviderFactory(context -> recordingProvider(calls, ""));
        builder.build().render(PARSER.parse("<div>\nfoo\n</div>\n"));
        assertEquals(List.of("p"), calls);
    }

    @Test
    public void attributeProviderNotCalledForRawHtmlBlock() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer.Builder builder = HtmlRenderer.builder().escapeHtml(false);
        builder.attributeProviderFactory(context -> recordingProvider(calls, ""));
        builder.build().render(PARSER.parse("<div>\nfoo\n</div>\n"));
        assertEquals(List.of(), calls);
    }

    @Test
    public void attributeProvidersCalledInRegistrationOrderPerTag() {
        List<String> calls = new ArrayList<>();
        rendererWithProviders(calls, "first:", "second:")
                .render(PARSER.parse("```java\nfoo\n```\n"));
        assertEquals(List.of("first:pre", "second:pre", "first:code", "second:code"), calls);
    }

    // Unlinking a node while it is being rendered

    private static NodeRenderer unlinkingThematicBreakRenderer(List<Node> rendered) {
        return new NodeRenderer() {
            @Override
            public Set<Class<? extends Node>> getNodeTypes() {
                return Set.of(ThematicBreak.class);
            }

            @Override
            public void render(Node node) {
                rendered.add(node);
                node.unlink();
            }
        };
    }

    @Test
    public void unlinkingNodeDuringHtmlRenderDoesNotStopFollowingSiblings() {
        List<Node> rendered = new ArrayList<>();
        HtmlRenderer renderer =
                HtmlRenderer.builder()
                        .nodeRendererFactory(context -> unlinkingThematicBreakRenderer(rendered))
                        .build();
        String html = renderer.render(PARSER.parse("foo\n\n***\n\nbar\n"));
        assertEquals("<p>foo</p>\n<p>bar</p>\n", html);
        assertEquals(1, rendered.size());
    }

    @Test
    public void unlinkingNodeDuringMarkdownRenderDoesNotStopFollowingSiblings() {
        List<Node> rendered = new ArrayList<>();
        MarkdownRenderer renderer =
                MarkdownRenderer.builder()
                        .nodeRendererFactory(
                                markdownFactory(unlinkingThematicBreakRenderer(rendered)))
                        .build();
        String markdown = renderer.render(PARSER.parse("foo\n\n***\n\nbar\n"));
        assertEquals("foo\n\nbar\n", markdown);
        assertEquals(1, rendered.size());
    }

    @Test
    public void unlinkingNodeDuringTextContentRenderDoesNotStopFollowingSiblings() {
        List<Node> rendered = new ArrayList<>();
        TextContentRenderer renderer =
                TextContentRenderer.builder()
                        .nodeRendererFactory(context -> unlinkingThematicBreakRenderer(rendered))
                        .build();
        String text = renderer.render(PARSER.parse("foo\n\n***\n\nbar\n"));
        assertEquals("foo\nbar", text);
        assertEquals(1, rendered.size());
    }

    @Test
    public void unlinkDetachesNodeAndReconnectsNeighbors() {
        Node document = PARSER.parse("a\n\nb\n\nc\n");
        Node first = document.getFirstChild();
        Node middle = first.getNext();
        Node last = middle.getNext();

        middle.unlink();

        assertNull(middle.getParent());
        assertNull(middle.getPrevious());
        assertNull(middle.getNext());
        assertSame(last, first.getNext());
        assertSame(first, last.getPrevious());
    }

    // render(Node, Appendable) writes output as it renders

    private static class StringBuilderAppendable implements Appendable {
        final StringBuilder sb = new StringBuilder();
        int appends;

        @Override
        public Appendable append(CharSequence csq) {
            appends++;
            sb.append(csq);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) {
            appends++;
            sb.append(csq, start, end);
            return this;
        }

        @Override
        public Appendable append(char c) {
            appends++;
            sb.append(c);
            return this;
        }
    }

    private static Node multiBlockDocument() {
        return PARSER.parse("# Title\n\npara one\n\npara two\n");
    }

    @Test
    public void renderToAppendableAppendsMultipleTimes() {
        HtmlRenderer htmlRenderer = HtmlRenderer.builder().build();
        MarkdownRenderer markdownRenderer = MarkdownRenderer.builder().build();
        TextContentRenderer textContentRenderer = TextContentRenderer.builder().build();

        StringBuilderAppendable html = new StringBuilderAppendable();
        htmlRenderer.render(multiBlockDocument(), html);
        assertTrue(html.appends > 1, "expected streaming output, got " + html.appends + " appends");
        assertEquals(htmlRenderer.render(multiBlockDocument()), html.sb.toString());

        StringBuilderAppendable markdown = new StringBuilderAppendable();
        markdownRenderer.render(multiBlockDocument(), markdown);
        assertTrue(
                markdown.appends > 1,
                "expected streaming output, got " + markdown.appends + " appends");
        assertEquals(markdownRenderer.render(multiBlockDocument()), markdown.sb.toString());

        StringBuilderAppendable text = new StringBuilderAppendable();
        textContentRenderer.render(multiBlockDocument(), text);
        assertTrue(text.appends > 1, "expected streaming output, got " + text.appends + " appends");
        assertEquals(textContentRenderer.render(multiBlockDocument()), text.sb.toString());
    }

    private static class FailingAppendable implements Appendable {
        final StringBuilder sb = new StringBuilder();
        final int limit;

        FailingAppendable(int limit) {
            this.limit = limit;
        }

        @Override
        public Appendable append(CharSequence csq) throws IOException {
            if (sb.length() + csq.length() > limit) {
                throw new IOException("boom");
            }
            sb.append(csq);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
            return append(csq.subSequence(start, end));
        }

        @Override
        public Appendable append(char c) throws IOException {
            return append(String.valueOf(c));
        }
    }

    private static void assertPartialOutputSurvives(
            org.commonmark.renderer.Renderer renderer, Node document) {
        String full = renderer.render(document);
        FailingAppendable failing = new FailingAppendable(full.length() / 2);
        RuntimeException e =
                assertThrows(RuntimeException.class, () -> renderer.render(document, failing));
        assertInstanceOf(IOException.class, e.getCause());
        String partial = failing.sb.toString();
        assertFalse(partial.isEmpty(), "expected partial output to survive the IOException");
        assertTrue(
                full.startsWith(partial),
                "partial output [" + partial + "] is not a prefix of [" + full + "]");
        assertTrue(partial.length() < full.length());
    }

    @Test
    public void ioExceptionDuringRenderLeavesPartialOutputInAppendable() {
        assertPartialOutputSurvives(HtmlRenderer.builder().build(), multiBlockDocument());
        assertPartialOutputSurvives(MarkdownRenderer.builder().build(), multiBlockDocument());
        assertPartialOutputSurvives(TextContentRenderer.builder().build(), multiBlockDocument());
    }

    // lastChar tracking of the writers: appending an empty string must not change it

    @Test
    public void htmlWriterEmptyAppendDoesNotChangeLastChar() {
        StringBuilder sb = new StringBuilder();
        HtmlWriter writer = new HtmlWriter(sb);
        writer.raw("a");
        writer.raw("");
        // If the empty append had reset lastChar, line() would not write the newline here
        writer.line();
        assertEquals("a\n", sb.toString());
    }

    @Test
    public void textContentWriterEmptyWriteDoesNotChangeLastChar() {
        StringBuilder sb = new StringBuilder();
        TextContentWriter writer = new TextContentWriter(sb);
        writer.write("a");
        writer.write("");
        // If the empty write had reset lastChar, whitespace() would not write the space here
        writer.whitespace();
        assertEquals("a ", sb.toString());
    }

    @Test
    public void markdownWriterEmptyRawDoesNotChangeLastChar() {
        StringBuilder sb = new StringBuilder();
        MarkdownWriter writer = new MarkdownWriter(sb);
        writer.raw("a");
        writer.raw("");
        assertEquals('a', writer.getLastChar());
    }

    // The three renderers escape the same input differently; pin each output

    @Test
    public void escapingDiffersBetweenRenderers() {
        Node document = PARSER.parse("x < y & \"z\" _em_ a_b\n");
        assertEquals(
                "<p>x &lt; y &amp; &quot;z&quot; <em>em</em> a_b</p>\n",
                HtmlRenderer.builder().build().render(document));
        assertEquals(
                "x \\< y \\& \"z\" _em_ a\\_b\n",
                MarkdownRenderer.builder().build().render(document));
        assertEquals(
                "x < y & \"z\" em a_b", TextContentRenderer.builder().build().render(document));
    }
}
