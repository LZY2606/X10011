package org.commonmark.internal.renderer;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;

/**
 * Base class for the core node renderers of the built-in renderers (HTML, Markdown, text content).
 * Implements the shared dispatching and child visiting logic; subclasses implement the {@code
 * visit} methods for the node types they support.
 */
public abstract class CoreNodeRenderer extends AbstractVisitor implements NodeRenderer {

    @Override
    public void render(Node node) {
        node.accept(this);
    }

    @Override
    protected void visitChildren(Node parent) {
        Node node = parent.getFirstChild();
        while (node != null) {
            // Get the next node before rendering, as rendering might modify the tree (e.g. unlink
            // the node).
            Node next = node.getNext();
            renderChild(node);
            node = next;
        }
    }

    /**
     * Render a child node by dispatching it back to the renderer context, so that custom node
     * renderers get a chance to handle it.
     *
     * @param node the child node to render
     */
    protected abstract void renderChild(Node node);
}
