package org.commonmark.internal.renderer;

import org.commonmark.node.Node;
import org.commonmark.renderer.Renderer;

/** Shared implementation bits for {@link Renderer} implementations. */
public final class RendererUtil {

    private RendererUtil() {}

    /**
     * Render the node to a string by rendering to a {@link StringBuilder} via {@link
     * Renderer#render(Node, Appendable)}.
     */
    public static String renderToString(Renderer renderer, Node node) {
        StringBuilder sb = new StringBuilder();
        renderer.render(node, sb);
        return sb.toString();
    }
}
