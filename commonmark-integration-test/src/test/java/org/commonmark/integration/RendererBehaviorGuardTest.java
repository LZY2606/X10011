package org.commonmark.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.commonmark.node.Document;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.NodeRenderer;
import org.commonmark.renderer.Renderer;
import org.commonmark.renderer.html.AttributeProvider;
import org.commonmark.renderer.html.HtmlNodeRendererContext;
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
 * Guards for observable behavior that differs (or must not change) between the three built-in
 * renderers. These pin the status quo so that internal deduplication can't accidentally flatten
 * behavioral differences.
 */
public class RendererBehaviorGuardTest {

    private static Node parse(String markdown) {
        return Parser.builder().build().parse(markdown);
    }

    // beforeRoot/afterRoot timing per renderer

    private static class RootCallbackRecorder implements NodeRenderer {
        private final List<String> events;

        RootCallbackRecorder(List<String> events) {
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

    @Test
    public void htmlRendererCallsBeforeAndAfterRootAroundRendering() {
        List<String> events = new ArrayList<>();
        RootCallbackRecorder recorder = new RootCallbackRecorder(events);
        HtmlRenderer renderer =
                HtmlRenderer.builder().nodeRendererFactory(context -> recorder).build();

        renderer.render(parse("hello\n"));

        assertThat(events).containsExactly("beforeRoot", "render", "afterRoot");
    }

    @Test
    public void markdownRendererDoesNotCallBeforeOrAfterRoot() {
        List<String> events = new ArrayList<>();
        RootCallbackRecorder recorder = new RootCallbackRecorder(events);
        MarkdownRenderer renderer =
                MarkdownRenderer.builder()
                        .nodeRendererFactory(
                                new MarkdownNodeRendererFactory() {
                                    @Override
                                    public NodeRenderer create(
                                            MarkdownNodeRendererContext context) {
                                        return recorder;
                                    }

                                    @Override
                                    public Set<Character> getSpecialCharacters() {
                                        return Set.of();
                                    }
                                })
                        .build();

        renderer.render(parse("hello\n"));

        assertThat(events).containsExactly("render");
    }

    @Test
    public void textContentRendererDoesNotCallBeforeOrAfterRoot() {
        List<String> events = new ArrayList<>();
        RootCallbackRecorder recorder = new RootCallbackRecorder(events);
        TextContentRenderer renderer =
                TextContentRenderer.builder().nodeRendererFactory(context -> recorder).build();

        renderer.render(parse("hello\n"));

        assertThat(events).containsExactly("render");
    }

    // AttributeProvider call count, tagName and ordering

    private static class RecordingAttributeProvider implements AttributeProvider {
        private final String name;
        private final List<String> calls;

        RecordingAttributeProvider(String name, List<String> calls) {
            this.name = name;
            this.calls = calls;
        }

        @Override
        public void setAttributes(Node node, String tagName, Map<String, String> attributes) {
            calls.add(name + ":" + tagName);
        }
    }

    private static HtmlRenderer rendererRecordingInto(
            List<String> calls, HtmlRenderer.Builder builder) {
        return builder.attributeProviderFactory(
                        context -> new RecordingAttributeProvider("A", calls))
                .build();
    }

    @Test
    public void attributeProviderCalledForPreAndCodeOfFencedCodeBlock() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer renderer = rendererRecordingInto(calls, HtmlRenderer.builder());

        renderer.render(parse("```java\ncode\n```\n"));

        assertThat(calls).containsExactly("A:pre", "A:code");
    }

    @Test
    public void attributeProviderCalledForParagraphAndImgOfImage() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer renderer = rendererRecordingInto(calls, HtmlRenderer.builder());

        renderer.render(parse("![alt](/url)\n"));

        assertThat(calls).containsExactly("A:p", "A:img");
    }

    @Test
    public void attributeProviderNotCalledForOmittedSingleParagraphP() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer renderer =
                rendererRecordingInto(calls, HtmlRenderer.builder().omitSingleParagraphP(true));

        renderer.render(parse("hello\n"));

        assertThat(calls).isEmpty();
    }

    @Test
    public void attributeProviderCalledForPOfEscapedHtmlBlock() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer renderer =
                rendererRecordingInto(calls, HtmlRenderer.builder().escapeHtml(true));

        renderer.render(parse("<div>\nhi\n</div>\n"));

        assertThat(calls).containsExactly("A:p");
    }

    @Test
    public void attributeProviderNotCalledForRawHtmlBlock() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer renderer =
                rendererRecordingInto(calls, HtmlRenderer.builder().escapeHtml(false));

        renderer.render(parse("<div>\nhi\n</div>\n"));

        assertThat(calls).isEmpty();
    }

    @Test
    public void attributeProvidersCalledInRegistrationOrder() {
        List<String> calls = new ArrayList<>();
        HtmlRenderer renderer =
                HtmlRenderer.builder()
                        .attributeProviderFactory(
                                context -> new RecordingAttributeProvider("A", calls))
                        .attributeProviderFactory(
                                context -> new RecordingAttributeProvider("B", calls))
                        .build();

        renderer.render(parse("```\ncode\n```\n"));

        assertThat(calls).containsExactly("A:pre", "B:pre", "A:code", "B:code");
    }

    // unlink during rendering

    private static void renderChildrenVia(HtmlNodeRendererContext context, Node node) {
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNext();
            context.render(child);
            child = next;
        }
    }

    @Test
    public void unlinkOfRenderedNodeDoesNotStopSiblingRendering() {
        Node document = parse("one\n\ntwo\n\nthree\n");
        HtmlRenderer renderer =
                HtmlRenderer.builder()
                        .nodeRendererFactory(
                                context ->
                                        new NodeRenderer() {
                                            @Override
                                            public Set<Class<? extends Node>> getNodeTypes() {
                                                return Set.of(Paragraph.class);
                                            }

                                            @Override
                                            public void render(Node node) {
                                                node.unlink();
                                                renderChildrenVia(context, node);
                                            }
                                        })
                        .build();

        String html = renderer.render(document);

        assertThat(html).contains("one").contains("two").contains("three");
        // All paragraphs unlinked themselves, so the document is childless now
        assertThat(document.getFirstChild()).isNull();
        assertThat(document.getLastChild()).isNull();
    }

    @Test
    public void childrenAreTraversedLazilyNotCollectedBeforeRendering() {
        Node document = parse("one\n\ntwo\n\nthree\n");
        Node firstParagraph = document.getFirstChild();
        Node thirdParagraph = firstParagraph.getNext().getNext();
        HtmlRenderer renderer =
                HtmlRenderer.builder()
                        .nodeRendererFactory(
                                context ->
                                        new NodeRenderer() {
                                            @Override
                                            public Set<Class<? extends Node>> getNodeTypes() {
                                                return Set.of(Paragraph.class);
                                            }

                                            @Override
                                            public void render(Node node) {
                                                if (node == firstParagraph) {
                                                    // Unlink a later sibling while an earlier one
                                                    // is being rendered
                                                    thirdParagraph.unlink();
                                                }
                                                renderChildrenVia(context, node);
                                            }
                                        })
                        .build();

        String html = renderer.render(document);

        // The traversal follows live sibling pointers (single pass, no snapshot list), so the
        // unlinked third paragraph is never reached.
        assertThat(html).contains("one").contains("two").doesNotContain("three");
    }

    @Test
    public void unlinkDetachesNodeAndRelinksFormerNeighbors() {
        Node document = parse("one\n\ntwo\n\nthree\n");
        Node first = document.getFirstChild();
        Node middle = first.getNext();
        Node last = middle.getNext();

        middle.unlink();

        assertThat(middle.getParent()).isNull();
        assertThat(middle.getPrevious()).isNull();
        assertThat(middle.getNext()).isNull();
        assertThat(first.getNext()).isSameAs(last);
        assertThat(last.getPrevious()).isSameAs(first);
        assertThat(document.getFirstChild()).isSameAs(first);
        assertThat(document.getLastChild()).isSameAs(last);
    }

    // streaming render(Node, Appendable)

    private static class CountingAppendable implements Appendable {
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

    @Test
    public void renderToAppendableStreamsOutputInMultipleAppends() {
        Node document = parse("# Title\n\nPara *one*\n\n- a\n- b\n");
        List<Renderer> renderers =
                List.of(
                        HtmlRenderer.builder().build(),
                        MarkdownRenderer.builder().build(),
                        TextContentRenderer.builder().build());

        for (Renderer renderer : renderers) {
            CountingAppendable out = new CountingAppendable();
            renderer.render(document, out);
            assertThat(out.appends).isGreaterThan(1);
            assertThat(out.sb.toString()).isEqualTo(renderer.render(document));
        }
    }

    private static class FailingAppendable implements Appendable {
        final StringBuilder sb = new StringBuilder();
        private final int failOnCall;
        private int calls;

        FailingAppendable(int failOnCall) {
            this.failOnCall = failOnCall;
        }

        private void maybeFail() throws IOException {
            calls++;
            if (calls >= failOnCall) {
                throw new IOException("boom");
            }
        }

        @Override
        public Appendable append(CharSequence csq) throws IOException {
            maybeFail();
            sb.append(csq);
            return this;
        }

        @Override
        public Appendable append(CharSequence csq, int start, int end) throws IOException {
            maybeFail();
            sb.append(csq, start, end);
            return this;
        }

        @Override
        public Appendable append(char c) throws IOException {
            maybeFail();
            sb.append(c);
            return this;
        }
    }

    @Test
    public void ioExceptionDuringRenderPropagatesAsRuntimeExceptionAndKeepsPartialOutput() {
        Node document = parse("# Title\n\nPara *one*\n\n- a\n- b\n");
        List<Renderer> renderers =
                List.of(
                        HtmlRenderer.builder().build(),
                        MarkdownRenderer.builder().build(),
                        TextContentRenderer.builder().build());

        for (Renderer renderer : renderers) {
            FailingAppendable out = new FailingAppendable(3);
            assertThatThrownBy(() -> renderer.render(document, out))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IOException.class);
            String partial = out.sb.toString();
            assertThat(partial).isNotEmpty();
            assertThat(renderer.render(document)).startsWith(partial);
        }
    }

    // lastChar semantics of the writers

    @Test
    public void htmlWriterEmptyAppendDoesNotChangeLastChar() {
        StringBuilder sb = new StringBuilder();
        HtmlWriter writer = new HtmlWriter(sb);
        writer.raw("a");
        writer.raw("");
        writer.line();
        assertThat(sb.toString()).isEqualTo("a\n");

        StringBuilder sb2 = new StringBuilder();
        HtmlWriter writer2 = new HtmlWriter(sb2);
        writer2.raw("");
        writer2.line();
        assertThat(sb2.toString()).isEmpty();
    }

    @Test
    public void textContentWriterEmptyWriteDoesNotChangeLastChar() {
        StringBuilder sb = new StringBuilder();
        TextContentWriter writer = new TextContentWriter(sb);
        writer.write("a");
        writer.write("");
        writer.whitespace();
        assertThat(sb.toString()).isEqualTo("a ");

        StringBuilder sb2 = new StringBuilder();
        TextContentWriter writer2 = new TextContentWriter(sb2);
        writer2.write("");
        writer2.whitespace();
        assertThat(sb2.toString()).isEmpty();
    }

    @Test
    public void markdownWriterEmptyRawDoesNotChangeLastChar() {
        StringBuilder sb = new StringBuilder();
        MarkdownWriter writer = new MarkdownWriter(sb);
        writer.raw("a");
        writer.raw("");
        assertThat(writer.getLastChar()).isEqualTo('a');

        MarkdownWriter writer2 = new MarkdownWriter(new StringBuilder());
        writer2.raw("");
        assertThat(writer2.getLastChar()).isEqualTo('\0');
    }

    // escaping differences between the three renderers

    @Test
    public void renderersEscapeSameTextDifferently() {
        Node document = parse("a < b & \"c\" _d_ *e*\n");

        assertThat(HtmlRenderer.builder().build().render(document))
                .isEqualTo("<p>a &lt; b &amp; &quot;c&quot; <em>d</em> <em>e</em></p>\n");
        assertThat(MarkdownRenderer.builder().build().render(document))
                .isEqualTo("a \\< b \\& \"c\" _d_ *e*\n");
        assertThat(TextContentRenderer.builder().build().render(document))
                .isEqualTo("a < b & \"c\" d e");
    }
}
