package org.commonmark.internal.renderer;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;

/**
 * Base class for node renderers that dispatch to {@code visit} methods via the visitor pattern.
 * Children are rendered through the node renderer map (see {@link #renderChild}), so that custom
 * node renderers get a chance to render them.
 */
public abstract class AbstractVisitorNodeRenderer extends AbstractVisitor implements NodeRenderer {

    @Override
    public void render(Node node) {
        node.accept(this);
    }

    @Override
    protected void visitChildren(Node parent) {
        Node node = parent.getFirstChild();
        while (node != null) {
            Node next = node.getNext();
            renderChild(node);
            node = next;
        }
    }

    /**
     * Render a child node by dispatching to the node renderer that is registered for its type
     * (usually via the renderer context).
     */
    protected abstract void renderChild(Node node);
}
