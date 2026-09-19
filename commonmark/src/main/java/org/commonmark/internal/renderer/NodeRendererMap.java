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

    /**
     * Create a map with a node renderer for each of the supplied factories, in order.
     *
     * @param nodeRendererFactories the factories to create node renderers with
     * @param createRenderer creates the node renderer for a factory
     * @return the map of node renderers
     * @param <F> the factory type
     */
    public static <F> NodeRendererMap create(
            Iterable<F> nodeRendererFactories, Function<F, NodeRenderer> createRenderer) {
        NodeRendererMap nodeRendererMap = new NodeRendererMap();
        for (F factory : nodeRendererFactories) {
            nodeRendererMap.add(createRenderer.apply(factory));
        }
        return nodeRendererMap;
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
