package org.commonmark.internal.renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.commonmark.node.Node;
import org.commonmark.renderer.NodeRenderer;

public class NodeRendererMap {

    private final List<NodeRenderer> nodeRenderers = new ArrayList<>();
    private final Map<Class<? extends Node>, NodeRenderer> renderers = new HashMap<>(32);

    /**
     * Create a node renderer for each factory (passing it the supplied context) and add it to a new
     * map.
     *
     * @param factories the factories to create node renderers with, in order
     * @param createRenderer creates a node renderer from a factory
     * @return the populated map
     */
    public static <F> NodeRendererMap of(
            Iterable<F> factories, Function<? super F, ? extends NodeRenderer> createRenderer) {
        NodeRendererMap nodeRendererMap = new NodeRendererMap();
        for (F factory : factories) {
            nodeRendererMap.add(createRenderer.apply(factory));
        }
        return nodeRendererMap;
    }

    /**
     * Set the renderer for each {@link NodeRenderer#getNodeTypes()}, unless there was already a
     * renderer set (first wins).
     */
    public void add(NodeRenderer nodeRenderer) {
        nodeRenderers.add(nodeRenderer);
        for (var nodeType : nodeRenderer.getNodeTypes()) {
            // The first node renderer for a node type "wins".
            renderers.putIfAbsent(nodeType, nodeRenderer);
        }
    }

    public void render(Node node) {
        var nodeRenderer = renderers.get(node.getClass());
        if (nodeRenderer != null) {
            nodeRenderer.render(node);
        }
    }

    public void beforeRoot(Node node) {
        nodeRenderers.forEach(r -> r.beforeRoot(node));
    }

    public void afterRoot(Node node) {
        nodeRenderers.forEach(r -> r.afterRoot(node));
    }
}
