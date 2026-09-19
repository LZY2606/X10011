package org.commonmark.internal.renderer;

import org.commonmark.node.Node;
import org.commonmark.renderer.Renderer;

/** Shared helpers for {@link Renderer} implementations. */
public final class Renderers {

    private Renderers() {}

    /**
     * Render the node to a string by rendering it into a {@link StringBuilder}.
     *
     * @param renderer the renderer to use
     * @param node the root node to render
     * @return the rendered output
     */
    public static String renderToString(Renderer renderer, Node node) {
        StringBuilder sb = new StringBuilder();
        renderer.render(node, sb);
        return sb.toString();
    }
}
