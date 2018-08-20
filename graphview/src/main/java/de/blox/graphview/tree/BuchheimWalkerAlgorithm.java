package de.blox.graphview.tree;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.blox.graphview.Algorithm;
import de.blox.graphview.EdgeRenderer;
import de.blox.graphview.Graph;
import de.blox.graphview.Node;
import de.blox.graphview.Vector;

public class BuchheimWalkerAlgorithm implements Algorithm {

    private BuchheimWalkerConfiguration configuration;
    private Map<Node, BuchheimWalkerNodeData> mNodeData = new HashMap<>();
    private EdgeRenderer edgeRenderer;
    private int minNodeHeight = Integer.MAX_VALUE;
    private int width;

    public BuchheimWalkerAlgorithm(BuchheimWalkerConfiguration configuration) {
        this.configuration = configuration;
        edgeRenderer = new TreeEdgeRenderer(configuration.getLevelSeparation());
    }

    /**
     * Creates a new BuchheimWalkerAlgorithm with default configuration.
     */
    public BuchheimWalkerAlgorithm() {
        this(new BuchheimWalkerConfiguration());
    }

    private static int compare(int x, int y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    private BuchheimWalkerNodeData createNodeData(Node node) {
        BuchheimWalkerNodeData nodeData = new BuchheimWalkerNodeData();
        nodeData.setAncestor(node);
        mNodeData.put(node, nodeData);

        return nodeData;
    }

    private BuchheimWalkerNodeData getNodeData(Node node) {
        return mNodeData.get(node);
    }

    private void firstWalk(Graph graph, Node node, int depth, int number) {
        BuchheimWalkerNodeData nodeData = createNodeData(node);
        nodeData.setDepth(depth);
        nodeData.setNumber(number);
        minNodeHeight = Math.min(minNodeHeight, node.getHeight());

        if (isLeaf(graph, node)) {
            // if the node has no left sibling, prelim(node) should be set to 0, but we don't have to set it
            // here, because it's already initialized with 0
            if (hasLeftSibling(graph, node)) {
                Node leftSibling = getLeftSibling(graph, node);
                nodeData.setPrelim(getPrelim(leftSibling) + getSpacing(graph, leftSibling, node));
            }
        } else {
            Node leftMost = getLeftMostChild(graph, node);
            Node rightMost = getRightMostChild(graph, node);
            Node defaultAncestor = leftMost;

            Node next = leftMost;
            int i = 1;
            while (next != null) {
                firstWalk(graph, next, depth + 1, i++);
                defaultAncestor = apportion(graph, next, defaultAncestor);

                next = getRightSibling(graph, next);
            }

            executeShifts(graph, node);

            double midPoint = 0.5 * ((getPrelim(leftMost) + getPrelim(rightMost) + rightMost.getWidth()) - node.getWidth());

            if (hasLeftSibling(graph, node)) {
                Node leftSibling = getLeftSibling(graph, node);
                nodeData.setPrelim(getPrelim(leftSibling) + getSpacing(graph, leftSibling, node));
                nodeData.setModifier(nodeData.getPrelim() - midPoint);
            } else {
                nodeData.setPrelim(midPoint);
            }
        }
    }

    private void secondWalk(Graph graph, Node node, double modifier) {
        BuchheimWalkerNodeData nodeData = getNodeData(node);
        final int depth = nodeData.getDepth();
        final int firstNodeWidth = graph.getNode(0).getWidth();
        node.setPos(new Vector((nodeData.getPrelim() + modifier) + width / 2 - firstNodeWidth / 2, depth));

        for (Node w : graph.findSuccessors(node)) {
            secondWalk(graph, w, modifier + nodeData.getModifier());
        }
    }

    private void executeShifts(Graph graph, Node node) {
        double shift = 0, change = 0;
        Node w = getRightMostChild(graph, node);
        while (w != null) {
            BuchheimWalkerNodeData nodeData = getNodeData(w);

            nodeData.setPrelim(nodeData.getPrelim() + shift);
            nodeData.setModifier(nodeData.getModifier() + shift);
            change += nodeData.getChange();
            shift += nodeData.getShift() + change;

            w = getLeftSibling(graph, w);
        }
    }

    private Node apportion(Graph graph, Node node, Node defaultAncestor) {
        if (hasLeftSibling(graph, node)) {
            Node leftSibling = getLeftSibling(graph, node);

            Node vip = node;
            Node vop = node;
            Node vim = leftSibling;
            Node vom = getLeftMostChild(graph, graph.findPredecessors(vip).get(0));

            double sip = getModifier(vip);
            double sop = getModifier(vop);
            double sim = getModifier(vim);
            double som = getModifier(vom);

            Node nextRight = nextRight(graph, vim);
            Node nextLeft = nextLeft(graph, vip);

            while (nextRight != null && nextLeft != null) {
                vim = nextRight;
                vip = nextLeft;
                vom = nextLeft(graph, vom);
                vop = nextRight(graph, vop);

                setAncestor(vop, node);

                double shift = (getPrelim(vim) + sim) - (getPrelim(vip) + sip) + getSpacing(graph, vim, node);
                if (shift > 0) {
                    moveSubtree(ancestor(graph, vim, node, defaultAncestor), node, shift);
                    sip += shift;
                    sop += shift;
                }

                sim += getModifier(vim);
                sip += getModifier(vip);
                som += getModifier(vom);
                sop += getModifier(vop);

                nextRight = nextRight(graph, vim);
                nextLeft = nextLeft(graph, vip);
            }

            if (nextRight != null && nextRight(graph, vop) == null) {
                setThread(vop, nextRight);
                setModifier(vop, getModifier(vop) + sim - sop);
            }

            if (nextLeft != null && nextLeft(graph, vom) == null) {
                setThread(vom, nextLeft);
                setModifier(vom, getModifier(vom) + sip - som);
                defaultAncestor = node;
            }
        }

        return defaultAncestor;
    }

    private void setAncestor(Node v, Node ancestor) {
        getNodeData(v).setAncestor(ancestor);
    }

    private void setModifier(Node v, double modifier) {
        getNodeData(v).setModifier(modifier);
    }

    private void setThread(Node v, Node thread) {
        getNodeData(v).setThread(thread);
    }

    private double getPrelim(Node v) {
        return getNodeData(v).getPrelim();
    }

    private double getModifier(Node vip) {
        return getNodeData(vip).getModifier();
    }

    private void moveSubtree(Node wm, Node wp, double shift) {
        BuchheimWalkerNodeData wpNodeData = getNodeData(wp);
        BuchheimWalkerNodeData wmNodeData = getNodeData(wm);

        int subtrees = wpNodeData.getNumber() - wmNodeData.getNumber();
        wpNodeData.setChange(wpNodeData.getChange() - shift / subtrees);
        wpNodeData.setShift(wpNodeData.getShift() + shift);
        wmNodeData.setChange(wmNodeData.getChange() + shift / subtrees);
        wpNodeData.setPrelim(wpNodeData.getPrelim() + shift);
        wpNodeData.setModifier(wpNodeData.getModifier() + shift);
    }

    private Node ancestor(Graph graph, Node vim, Node node, Node defaultAncestor) {
        BuchheimWalkerNodeData vipNodeData = getNodeData(vim);

        if (graph.findPredecessors(vipNodeData.getAncestor()).get(0) == graph.findPredecessors(node).get(0)) {
            return vipNodeData.getAncestor();
        }

        return defaultAncestor;
    }

    private Node nextRight(Graph graph, Node node) {
        if (graph.hasSuccessor(node)) {
            return getRightMostChild(graph, node);
        }

        return getNodeData(node).getThread();
    }

    private Node nextLeft(Graph graph, Node node) {
        if (graph.hasSuccessor(node)) {
            return getLeftMostChild(graph, node);
        }

        return getNodeData(node).getThread();
    }

    private int getSpacing(Graph graph, Node leftNode, Node rightNode) {
        int separation = configuration.getSubtreeSeparation();

        if (isSibling(graph, leftNode, rightNode)) {
            separation = configuration.getSiblingSeparation();
        }

        return separation + leftNode.getWidth();
    }

    private boolean isSibling(Graph graph, Node leftNode, Node rightNode) {
        Node leftParent = graph.findPredecessors(leftNode).get(0);
        return graph.findSuccessors(leftParent).contains(rightNode);

    }

    private boolean isLeaf(Graph graph, Node node) {
        return graph.findSuccessors(node).isEmpty();
    }

    private Node getLeftSibling(Graph graph, Node node) {
        if (!hasLeftSibling(graph, node)) {
            return null;
        }

        Node parent = graph.findPredecessors(node).get(0);
        List<Node> children = graph.findSuccessors(parent);
        int nodeIndex = children.indexOf(node);
        return children.get(nodeIndex - 1);
    }

    private boolean hasLeftSibling(Graph graph, Node node) {
        List<Node> parents = graph.findPredecessors(node);
        if (parents.isEmpty()) {
            return false;
        }

        Node parent = parents.get(0);
        int nodeIndex = graph.findSuccessors(parent).indexOf(node);
        return nodeIndex > 0;
    }

    private Node getRightSibling(Graph graph, Node node) {
        if (!hasRightSibling(graph, node)) {
            return null;
        }

        Node parent = graph.findPredecessors(node).get(0);
        List<Node> children = graph.findSuccessors(parent);
        int nodeIndex = children.indexOf(node);
        return children.get(nodeIndex + 1);
    }

    private boolean hasRightSibling(Graph graph, Node node) {
        List<Node> parents = graph.findPredecessors(node);
        if (parents.isEmpty()) {
            return false;
        }
        Node parent = parents.get(0);
        List<Node> children = graph.findSuccessors(parent);
        int nodeIndex = children.indexOf(node);
        return nodeIndex < children.size() - 1;
    }

    private Node getLeftMostChild(Graph graph, Node node) {
        return graph.findSuccessors(node).get(0);
    }

    private Node getRightMostChild(Graph graph, Node node) {
        List<Node> children = graph.findSuccessors(node);
        if (children.isEmpty()) {
            return null;
        }

        return children.get(children.size() - 1);
    }

    @Override
    public void run(Graph graph) {
        // TODO check for cycles and multiple parents
        mNodeData.clear();

        final Node firstNode = graph.getNode(0);
        firstWalk(graph, firstNode, 0, 0);


        secondWalk(graph, firstNode, -getPrelim(firstNode));

        positionNodes(graph);
    }

    private void positionNodes(Graph graph) {
        int globalPadding = 0;
        int localPadding = 0;
        int currentLevel = 0;

        for (Node node : sortByLevel(graph)) {
            final int height = node.getHeight();
            if (height > minNodeHeight) {
                localPadding = Math.max(localPadding, height - minNodeHeight);
            }

            int depth = getNodeData(node).getDepth();
            if (depth != currentLevel) {
                globalPadding += localPadding;
                localPadding = 0;
                currentLevel = depth;
            }

            node.setPos(new Vector(node.getX(), node.getY() * minNodeHeight + (depth * configuration.getLevelSeparation()) + globalPadding));
        }
    }

    private List<Node> sortByLevel(Graph graph) {
        List<Node> nodes = new ArrayList<>(graph.getNodes());

        Collections.sort(nodes, new Comparator<Node>() {
            @Override
            public int compare(Node o1, Node o2) {
                final BuchheimWalkerNodeData data1 = getNodeData(o1);
                final BuchheimWalkerNodeData data2 = getNodeData(o2);
                return BuchheimWalkerAlgorithm.compare(data1.getDepth(), data2.getDepth());
            }
        });

        return nodes;
    }

    @Override
    public void drawEdges(Canvas canvas, Graph graph, Paint linePaint) {
        edgeRenderer.render(canvas, graph, linePaint);
    }

    @Override
    public void setMeasuredDimension(int width, int height) {
        this.width = width;
    }
}
