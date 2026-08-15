package com.gitquest.ui.campaign;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.gitquest.core.campaign.CampaignCatalog;
import com.gitquest.core.campaign.CampaignCatalog.ArcInfo;
import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.ui.sandbox.LanePalette;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;

/**
 * Renders the Campaign skill tree in the same visual language as the
 * Sandbox commit graph — CLAUDE.md's whole pitch is "Git becomes visible",
 * so the skill tree looks like one too: a vertical spine with one node per
 * arc (like a linear commit history), and each unlocked arc's levels
 * branching off to the right as a short horizontal chain, colored per arc
 * via the same {@link LanePalette} used for branch lanes in Sandbox.
 */
public final class SkillTreeView extends Pane {

    private static final double SPINE_X = 60;
    private static final double MARGIN_Y = 60;
    private static final double ROW_HEIGHT = 120;
    private static final double NODE_SPACING = 150;
    private static final double RADIUS = 22;
    private static final double SPINE_RADIUS = 12;

    private static final Color LOCKED_FILL = Color.web("#3A3A3C");
    private static final Color SPINE_EDGE = Color.web("#5B6472");
    private static final Color STROKE = Color.web("#0C0C0D");
    private static final Color COMPLETED_FILL = Color.web("#F05133");

    private final Group edgesLayer = new Group();
    private final Group nodesLayer = new Group();

    public SkillTreeView() {
        getStyleClass().add("commit-graph");
        getChildren().addAll(edgesLayer, nodesLayer);
    }

    public void render(List<ArcInfo> arcs, CampaignProgress progress, Consumer<LevelDefinition> onLevelClicked) {
        edgesLayer.getChildren().clear();
        nodesLayer.getChildren().clear();

        Map<Integer, Double> spineY = new HashMap<>();
        double maxX = SPINE_X;

        for (int arcIndex = 0; arcIndex < arcs.size(); arcIndex++) {
            ArcInfo arc = arcs.get(arcIndex);
            double y = MARGIN_Y + arcIndex * ROW_HEIGHT;
            spineY.put(arcIndex, y);

            boolean unlocked = progress.isArcUnlocked(arc.id());
            List<LevelDefinition> levels = CampaignCatalog.levelsForArc(arc.id());
            Color arcColor = LanePalette.forLane(arcIndex);

            addSpineNode(SPINE_X, y, arc, unlocked, levels.isEmpty(), arcColor);

            double previousX = SPINE_X;
            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                LevelDefinition level = levels.get(levelIndex);
                double x = SPINE_X + (levelIndex + 1) * NODE_SPACING;
                maxX = Math.max(maxX, x);
                addEdge(previousX, y, x, y, unlocked ? arcColor : LOCKED_FILL);
                addLevelNode(x, y, level, progress, unlocked, arcColor, onLevelClicked);
                previousX = x;
            }
        }

        for (int arcIndex = 0; arcIndex < arcs.size() - 1; arcIndex++) {
            addEdge(SPINE_X, spineY.get(arcIndex), SPINE_X, spineY.get(arcIndex + 1), SPINE_EDGE);
        }

        setPrefWidth(maxX + 160);
        setPrefHeight(MARGIN_Y + arcs.size() * ROW_HEIGHT + 40);
    }

    private void addSpineNode(double x, double y, ArcInfo arc, boolean unlocked, boolean noLevelsYet, Color arcColor) {
        Circle circle = new Circle(x, y, SPINE_RADIUS);
        circle.setFill(unlocked ? arcColor : LOCKED_FILL);
        circle.setStroke(STROKE);
        circle.setStrokeWidth(1.5);
        Tooltip.install(circle, new Tooltip(arc.title() + "\n" + arc.description()));

        Label title = new Label((unlocked ? "" : "🔒 ") + arc.title() + (noLevelsYet ? "  (coming soon)" : ""));
        title.setLayoutX(x - SPINE_RADIUS);
        title.setLayoutY(y - SPINE_RADIUS - 20);
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: "
                + (unlocked ? "#E6E6E6" : "#9DA5B4") + ";");

        nodesLayer.getChildren().addAll(circle, title);
    }

    private void addLevelNode(double x, double y, LevelDefinition level, CampaignProgress progress,
            boolean arcUnlocked, Color arcColor, Consumer<LevelDefinition> onLevelClicked) {
        boolean completed = progress.isLevelCompleted(level.id());

        Circle circle = new Circle(x, y, RADIUS);
        circle.setFill(!arcUnlocked ? LOCKED_FILL : (completed ? COMPLETED_FILL : arcColor));
        circle.setStroke(completed ? COMPLETED_FILL : STROKE);
        circle.setStrokeWidth(completed ? 3 : 1.5);
        Tooltip.install(circle, new Tooltip(level.title() + "\n"
                + (completed ? "Completed — click to replay" : (arcUnlocked ? "Click to play" : "Locked"))));

        Text glyph = new Text(completed ? "✓" : (arcUnlocked ? "▶" : "🔒"));
        glyph.setStyle("-fx-font-size: 16px; -fx-fill: white;");
        glyph.setLayoutX(x - 6);
        glyph.setLayoutY(y + 6);

        Label title = new Label(level.title());
        title.setWrapText(true);
        title.setPrefWidth(NODE_SPACING - 20);
        title.setAlignment(Pos.TOP_CENTER);
        title.setLayoutX(x - (NODE_SPACING - 20) / 2);
        title.setLayoutY(y + RADIUS + 6);
        title.setStyle("-fx-font-size: 11px; -fx-text-fill: #E6E6E6;");

        if (arcUnlocked) {
            circle.setCursor(Cursor.HAND);
            circle.setOnMouseClicked(e -> onLevelClicked.accept(level));
        }

        nodesLayer.getChildren().addAll(circle, glyph, title);
    }

    private void addEdge(double x1, double y1, double x2, double y2, Color color) {
        Line line = new Line(x1, y1, x2, y2);
        line.setStroke(color);
        line.setStrokeWidth(2);
        line.setOpacity(0.6);
        edgesLayer.getChildren().add(line);
    }
}
