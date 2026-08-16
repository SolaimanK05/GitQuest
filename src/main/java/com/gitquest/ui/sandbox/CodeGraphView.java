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
 * animation. Click a node to focus it: everything except its direct
 * dependencies/dependents (1-hop) dims, so the graph stays readable
 * instead of degrading into spaghetti.
 */
public final class CodeGraphView extends Pane {

    private static final double REPULSION = 2400;
    private static final double SPRING_LENGTH = 100;
    private static final double SPRING_STRENGTH = 0.02;
    private static final double CENTERING_STRENGTH = 0.008;
    private static final double DAMPING = 0.85;
    private static final double SETTLE_SPEED_THRESHOLD = 0.05;
    private static final int SETTLE_FRAME_COUNT = 30;
    private static final Color EDGE_COLOR = Color.web("#5B6472");
    private static final Color FOCUS_STROKE = Color.web("#F05133");

    private final Group edgesLayer = new Group();
    private final Group nodesLayer = new Group();
    private final Map<String, CodeGraphNodeView> nodesByPath = new LinkedHashMap<>();
    private final Map<DependencyEdge, Line> lineByEdge = new HashMap<>();
    private List<DependencyEdge> edges = List.of();

    private AnimationTimer timer;
    private double canvasWidth = 600;
    private double canvasHeight = 400;
    private int settledFrames;
    private String focusedPath;
    private Consumer<String> onNodeSelected = path -> { };

    public CodeGraphView() {
        getStyleClass().add("commit-graph");
        getChildren().addAll(edgesLayer, nodesLayer);
    }

    public void setOnNodeSelected(Consumer<String> handler) {
        this.onNodeSelected = handler;
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
        focusedPath = null;

        Random random = new Random(1); // deterministic initial scatter — reproducible layouts between renders
        for (String path : graph.filePaths()) {
            CodeGraphNodeView node = new CodeGraphNodeView(path, graph.filesWithParseErrors().contains(path));
            node.x = canvasWidth / 2 + (random.nextDouble() - 0.5) * canvasWidth * 0.7;
            node.y = canvasHeight / 2 + (random.nextDouble() - 0.5) * canvasHeight * 0.7;
            node.circle.setFill(LanePalette.forLane(path.hashCode()));
            node.circle.setCursor(Cursor.HAND);
            node.circle.setOnMouseClicked(e -> selectNode(node.path));
            nodesByPath.put(path, node);
            nodesLayer.getChildren().addAll(node.circle, node.label);
        }

        edges = graph.edges();
        for (DependencyEdge edge : edges) {
            CodeGraphNodeView from = nodesByPath.get(edge.fromPath());
            CodeGraphNodeView to = nodesByPath.get(edge.toPath());
            if (from == null || to == null) {
                continue;
            }
            Line line = new Line();
            line.setStroke(EDGE_COLOR);
            line.setStrokeWidth(1.2);
            line.setOpacity(0.5);
            Tooltip.install(line, new Tooltip(edge.fromPath() + " uses " + edge.toPath()
                    + "\n(" + String.join(", ", edge.referencedTypeNames()) + ")"));
            lineByEdge.put(edge, line);
            edgesLayer.getChildren().add(line);
        }

        startPhysics();
    }

    /** Dims every node/edge not within 1 hop of {@code path}, or clears the focus entirely for {@code null}. */
    public void focusOn(String path) {
        focusedPath = path;
        applyFocusStyling();
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
        focusOn(focusedPath != null && focusedPath.equals(path) ? null : path);
        onNodeSelected.accept(focusedPath);
    }

    private void applyFocusStyling() {
        if (focusedPath == null) {
            nodesByPath.values().forEach(node -> {
                node.circle.setOpacity(1);
                node.label.setOpacity(1);
                node.circle.setStroke(Color.web("#0C0C0D"));
                node.circle.setStrokeWidth(1.5);
            });
            lineByEdge.values().forEach(line -> line.setOpacity(0.5));
            return;
        }

        Set<String> connected = new HashSet<>();
        connected.add(focusedPath);
        for (DependencyEdge edge : edges) {
            if (edge.fromPath().equals(focusedPath)) {
                connected.add(edge.toPath());
            }
            if (edge.toPath().equals(focusedPath)) {
                connected.add(edge.fromPath());
            }
        }

        for (Map.Entry<String, CodeGraphNodeView> entry : nodesByPath.entrySet()) {
            boolean relevant = connected.contains(entry.getKey());
            CodeGraphNodeView node = entry.getValue();
            node.circle.setOpacity(relevant ? 1 : 0.15);
            node.label.setOpacity(relevant ? 1 : 0.15);
            boolean isFocused = entry.getKey().equals(focusedPath);
            node.circle.setStroke(isFocused ? FOCUS_STROKE : Color.web("#0C0C0D"));
            node.circle.setStrokeWidth(isFocused ? 3 : 1.5);
        }
        for (DependencyEdge edge : edges) {
            boolean relevant = edge.fromPath().equals(focusedPath) || edge.toPath().equals(focusedPath);
            Line line = lineByEdge.get(edge);
            if (line != null) {
                line.setOpacity(relevant ? 0.9 : 0.05);
            }
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

    /** One physics step: pairwise repulsion, per-edge springs, mild centering, then integrate. Returns true if nearly at rest. */
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
            node.vx *= DAMPING;
            node.vy *= DAMPING;
            node.x = clamp(node.x + node.vx, CodeGraphNodeView.RADIUS, canvasWidth - CodeGraphNodeView.RADIUS);
            node.y = clamp(node.y + node.vy, CodeGraphNodeView.RADIUS, canvasHeight - CodeGraphNodeView.RADIUS);
            totalSpeed += Math.abs(node.vx) + Math.abs(node.vy);

            node.circle.setCenterX(node.x);
            node.circle.setCenterY(node.y);
            node.label.setLayoutX(node.x + CodeGraphNodeView.RADIUS + 4);
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

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
