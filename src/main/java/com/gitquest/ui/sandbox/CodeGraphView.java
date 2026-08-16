package com.gitquest.ui.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import com.gitquest.core.codegraph.DependencyEdge;
import com.gitquest.core.codegraph.JavaDependencyGraph;

import javafx.animation.AnimationTimer;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * Java-scoped file dependency graph (CLAUDE.md 4.3 Tier 1) as a force-
 * directed node-link diagram: nodes repel each other, edges act as
 * springs pulling their two files together, both driven by a continuous
 * {@link AnimationTimer} physics tick rather than discrete keyframes, per
 * CLAUDE.md Section 6 — this is a running simulation, not a fixed
 * animation.
 *
 * <p>Files in the same folder share a color and a gentle "cluster" pull
 * toward a shared anchor point, so directory structure reads visually
 * even though edges alone wouldn't group unconnected sibling files
 * together. A node's radius scales with its connection count (in +
 * out), so heavily-depended-on "hub" files read as bigger at a glance.
 *
 * <p>Click a node to focus it: everything except its direct dependencies/
 * dependents (1-hop) dims. Click an edge instead to focus just that one
 * connection and see exactly what's used — the tooltip/detail callback
 * carries both the class/interface names referenced and (best-effort,
 * see {@code JavaDependencyAnalyzer}) the specific method calls resolved
 * back to that file. Clicking empty canvas clears whichever is focused.
 */
public final class CodeGraphView extends Pane {

    private static final double REPULSION = 2400;
    private static final double SPRING_LENGTH = 100;
    private static final double SPRING_STRENGTH = 0.02;
    private static final double CENTERING_STRENGTH = 0.003;
    private static final double CLUSTER_STRENGTH = 0.012;
    private static final double DAMPING = 0.85;
    private static final double SETTLE_SPEED_THRESHOLD = 0.05;
    private static final int SETTLE_FRAME_COUNT = 30;
    private static final double MIN_RADIUS = 8;
    private static final double MAX_RADIUS = 22;
    private static final double MIN_EDGE_WIDTH = 1.4;
    private static final double MAX_EDGE_WIDTH = 4.5;
    private static final Color EDGE_COLOR = Color.web("#5B6472");
    private static final Color FOCUS_STROKE = Color.web("#F05133");

    private final Group edgesLayer = new Group();
    private final Group nodesLayer = new Group();
    private final Group folderLabelsLayer = new Group();
    private final Map<String, CodeGraphNodeView> nodesByPath = new LinkedHashMap<>();
    private final Map<DependencyEdge, Line> lineByEdge = new HashMap<>();
    private List<DependencyEdge> edges = List.of();

    private AnimationTimer timer;
    private double canvasWidth = 600;
    private double canvasHeight = 400;
    private int settledFrames;
    private String focusedPath;
    private DependencyEdge focusedEdge;
    private Consumer<String> onNodeSelected = path -> { };
    private Consumer<DependencyEdge> onEdgeSelected = edge -> { };

    public CodeGraphView() {
        getStyleClass().add("commit-graph");
        getChildren().addAll(edgesLayer, nodesLayer, folderLabelsLayer);
        // A click that reaches the Pane itself (not a node/edge child) is a click on empty canvas.
        setOnMouseClicked(e -> {
            if (e.getTarget() == this) {
                clearFocus();
            }
        });
    }

    public void setOnNodeSelected(Consumer<String> handler) {
        this.onNodeSelected = handler;
    }

    public void setOnEdgeSelected(Consumer<DependencyEdge> handler) {
        this.onEdgeSelected = handler;
    }

    /** (Re)builds the graph from scratch and starts the physics simulation settling it into place. */
    public void render(JavaDependencyGraph graph, double width, double height) {
        stopPhysics();
        canvasWidth = Math.max(width, 200);
        canvasHeight = Math.max(height, 200);
        setPrefSize(canvasWidth, canvasHeight);

        nodesByPath.clear();
        lineByEdge.clear();
        edgesLayer.getChildren().clear();
        nodesLayer.getChildren().clear();
        folderLabelsLayer.getChildren().clear();
        focusedPath = null;
        focusedEdge = null;
        edges = graph.edges();

        Map<String, Integer> degree = new HashMap<>();
        for (DependencyEdge edge : edges) {
            degree.merge(edge.fromPath(), 1, Integer::sum);
            degree.merge(edge.toPath(), 1, Integer::sum);
        }

        Map<String, List<String>> pathsByFolder = new LinkedHashMap<>();
        for (String path : graph.filePaths()) {
            pathsByFolder.computeIfAbsent(folderOf(path), unused -> new ArrayList<>()).add(path);
        }
        Map<String, double[]> anchorByFolder = clusterAnchors(pathsByFolder.keySet());

        Random random = new Random(1); // deterministic initial scatter — reproducible layouts between renders
        for (String path : graph.filePaths()) {
            String folder = folderOf(path);
            double radius = Math.min(MIN_RADIUS + Math.sqrt(degree.getOrDefault(path, 0)) * 6, MAX_RADIUS);
            CodeGraphNodeView node = new CodeGraphNodeView(path, folder, radius, graph.filesWithParseErrors().contains(path));
            double[] anchor = anchorByFolder.get(folder);
            node.anchorX = anchor[0];
            node.anchorY = anchor[1];
            node.x = anchor[0] + (random.nextDouble() - 0.5) * 80;
            node.y = anchor[1] + (random.nextDouble() - 0.5) * 80;
            node.circle.setFill(LanePalette.forLane(folder.hashCode()));
            node.circle.setCursor(Cursor.HAND);
            node.circle.setOnMouseClicked(e -> selectNode(node.path));
            nodesByPath.put(path, node);
            nodesLayer.getChildren().addAll(node.circle, node.label);
        }

        for (Map.Entry<String, double[]> entry : anchorByFolder.entrySet()) {
            if (entry.getKey().isEmpty() || pathsByFolder.getOrDefault(entry.getKey(), List.of()).size() < 2) {
                continue; // no point labeling a single-file "cluster", or the implicit root folder
            }
            Label folderLabel = new Label(entry.getKey() + "/");
            folderLabel.setMouseTransparent(true);
            folderLabel.setStyle("-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: #9DA5B4;");
            folderLabel.setLayoutX(entry.getValue()[0] - 20);
            folderLabel.setLayoutY(entry.getValue()[1] - 34);
            folderLabelsLayer.getChildren().add(folderLabel);
        }

        for (DependencyEdge edge : edges) {
            CodeGraphNodeView from = nodesByPath.get(edge.fromPath());
            CodeGraphNodeView to = nodesByPath.get(edge.toPath());
            if (from == null || to == null) {
                continue;
            }
            Line line = new Line();
            line.setStroke(EDGE_COLOR);
            line.setStrokeWidth(edgeWidth(edge));
            line.setOpacity(0.5);
            line.setCursor(Cursor.HAND);
            line.setOnMouseClicked(e -> selectEdge(edge));
            Tooltip.install(line, new Tooltip(edge.fromPath() + " -> " + edge.toPath() + describeUsage(edge)));
            lineByEdge.put(edge, line);
            edgesLayer.getChildren().add(line);
        }

        startPhysics();
    }

    /** Dims every node/edge not within 1 hop of {@code path}, or clears the focus entirely for {@code null}. */
    public void focusOn(String path) {
        focusedPath = path;
        focusedEdge = null;
        applyFocusStyling();
    }

    public void clearFocus() {
        focusedPath = null;
        focusedEdge = null;
        applyFocusStyling();
        onNodeSelected.accept(null);
        onEdgeSelected.accept(null);
    }

    public void stopPhysics() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /** Resumes the settling simulation from wherever the nodes currently sit — e.g. after switching back to this tab. */
    public void resumePhysics() {
        if (timer == null && !nodesByPath.isEmpty()) {
            startPhysics();
        }
    }

    private void selectNode(String path) {
        focusedEdge = null;
        focusedPath = focusedPath != null && focusedPath.equals(path) ? null : path;
        applyFocusStyling();
        onNodeSelected.accept(focusedPath);
    }

    private void selectEdge(DependencyEdge edge) {
        focusedPath = null;
        focusedEdge = edge.equals(focusedEdge) ? null : edge;
        applyFocusStyling();
        onEdgeSelected.accept(focusedEdge);
    }

    private void applyFocusStyling() {
        if (focusedPath == null && focusedEdge == null) {
            nodesByPath.values().forEach(node -> {
                node.circle.setOpacity(1);
                node.label.setOpacity(1);
                node.circle.setStroke(Color.web("#0C0C0D"));
                node.circle.setStrokeWidth(1.5);
            });
            for (DependencyEdge edge : edges) {
                Line line = lineByEdge.get(edge);
                if (line != null) {
                    line.setOpacity(0.5);
                    line.setStroke(EDGE_COLOR);
                }
            }
            return;
        }

        Set<String> relevantNodes = new HashSet<>();
        Set<DependencyEdge> relevantEdges = new HashSet<>();
        if (focusedPath != null) {
            relevantNodes.add(focusedPath);
            for (DependencyEdge edge : edges) {
                if (edge.fromPath().equals(focusedPath)) {
                    relevantNodes.add(edge.toPath());
                    relevantEdges.add(edge);
                }
                if (edge.toPath().equals(focusedPath)) {
                    relevantNodes.add(edge.fromPath());
                    relevantEdges.add(edge);
                }
            }
        } else {
            relevantNodes.add(focusedEdge.fromPath());
            relevantNodes.add(focusedEdge.toPath());
            relevantEdges.add(focusedEdge);
        }

        for (Map.Entry<String, CodeGraphNodeView> entry : nodesByPath.entrySet()) {
            boolean relevant = relevantNodes.contains(entry.getKey());
            CodeGraphNodeView node = entry.getValue();
            node.circle.setOpacity(relevant ? 1 : 0.15);
            node.label.setOpacity(relevant ? 1 : 0.15);
            boolean isFocusedNode = entry.getKey().equals(focusedPath);
            node.circle.setStroke(isFocusedNode ? FOCUS_STROKE : Color.web("#0C0C0D"));
            node.circle.setStrokeWidth(isFocusedNode ? 3 : 1.5);
        }
        for (DependencyEdge edge : edges) {
            Line line = lineByEdge.get(edge);
            if (line == null) {
                continue;
            }
            boolean relevant = relevantEdges.contains(edge);
            line.setOpacity(relevant ? 0.9 : 0.05);
            line.setStroke(edge.equals(focusedEdge) ? FOCUS_STROKE : EDGE_COLOR);
        }
    }

    private void startPhysics() {
        settledFrames = 0;
        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                boolean settled = tick();
                if (settled) {
                    settledFrames++;
                    if (settledFrames > SETTLE_FRAME_COUNT) {
                        stopPhysics(); // the layout has converged — no point burning cycles on a static picture
                    }
                } else {
                    settledFrames = 0;
                }
            }
        };
        timer.start();
    }

    /** One physics step: pairwise repulsion, per-edge springs, centering + per-folder clustering, then integrate. */
    private boolean tick() {
        List<CodeGraphNodeView> nodes = new ArrayList<>(nodesByPath.values());

        for (int i = 0; i < nodes.size(); i++) {
            CodeGraphNodeView a = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                CodeGraphNodeView b = nodes.get(j);
                double dx = a.x - b.x;
                double dy = a.y - b.y;
                double distSq = Math.max(dx * dx + dy * dy, 25);
                double dist = Math.sqrt(distSq);
                double force = REPULSION / distSq;
                double fx = force * dx / dist;
                double fy = force * dy / dist;
                a.vx += fx;
                a.vy += fy;
                b.vx -= fx;
                b.vy -= fy;
            }
        }

        for (DependencyEdge edge : edges) {
            CodeGraphNodeView from = nodesByPath.get(edge.fromPath());
            CodeGraphNodeView to = nodesByPath.get(edge.toPath());
            if (from == null || to == null) {
                continue;
            }
            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double dist = Math.max(Math.sqrt(dx * dx + dy * dy), 1);
            double force = SPRING_STRENGTH * (dist - SPRING_LENGTH);
            double fx = force * dx / dist;
            double fy = force * dy / dist;
            from.vx += fx;
            from.vy += fy;
            to.vx -= fx;
            to.vy -= fy;
        }

        double totalSpeed = 0;
        for (CodeGraphNodeView node : nodes) {
            node.vx += (canvasWidth / 2 - node.x) * CENTERING_STRENGTH;
            node.vy += (canvasHeight / 2 - node.y) * CENTERING_STRENGTH;
            node.vx += (node.anchorX - node.x) * CLUSTER_STRENGTH;
            node.vy += (node.anchorY - node.y) * CLUSTER_STRENGTH;
            node.vx *= DAMPING;
            node.vy *= DAMPING;
            node.x = clamp(node.x + node.vx, node.radius, canvasWidth - node.radius);
            node.y = clamp(node.y + node.vy, node.radius, canvasHeight - node.radius);
            totalSpeed += Math.abs(node.vx) + Math.abs(node.vy);

            node.circle.setCenterX(node.x);
            node.circle.setCenterY(node.y);
            node.label.setLayoutX(node.x + node.radius + 4);
            node.label.setLayoutY(node.y - 8);
        }

        for (DependencyEdge edge : edges) {
            Line line = lineByEdge.get(edge);
            CodeGraphNodeView from = nodesByPath.get(edge.fromPath());
            CodeGraphNodeView to = nodesByPath.get(edge.toPath());
            if (line == null || from == null || to == null) {
                continue;
            }
            line.setStartX(from.x);
            line.setStartY(from.y);
            line.setEndX(to.x);
            line.setEndY(to.y);
        }

        return nodes.isEmpty() || totalSpeed / nodes.size() < SETTLE_SPEED_THRESHOLD;
    }

    /** One anchor point per distinct folder, spread evenly around the canvas center. */
    private Map<String, double[]> clusterAnchors(Set<String> folders) {
        Map<String, double[]> anchors = new LinkedHashMap<>();
        List<String> ordered = new ArrayList<>(folders);
        int count = ordered.size();
        double clusterRadius = Math.min(canvasWidth, canvasHeight) * 0.32;
        for (int i = 0; i < count; i++) {
            double angle = count <= 1 ? 0 : (2 * Math.PI * i) / count;
            double ax = canvasWidth / 2 + (count <= 1 ? 0 : clusterRadius * Math.cos(angle));
            double ay = canvasHeight / 2 + (count <= 1 ? 0 : clusterRadius * Math.sin(angle));
            anchors.put(ordered.get(i), new double[] {ax, ay});
        }
        return anchors;
    }

    private static double edgeWidth(DependencyEdge edge) {
        int usageCount = edge.referencedTypeNames().size() + edge.referencedMethodNames().size();
        return Math.min(MIN_EDGE_WIDTH + usageCount * 0.4, MAX_EDGE_WIDTH);
    }

    private static String describeUsage(DependencyEdge edge) {
        StringBuilder text = new StringBuilder();
        if (!edge.referencedTypeNames().isEmpty()) {
            text.append("\nType(s): ").append(String.join(", ", edge.referencedTypeNames()));
        }
        if (!edge.referencedMethodNames().isEmpty()) {
            text.append("\nCall(s): ").append(String.join(", ", edge.referencedMethodNames()));
        }
        return text.toString();
    }

    private static String folderOf(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(0, lastSlash) : "";
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
