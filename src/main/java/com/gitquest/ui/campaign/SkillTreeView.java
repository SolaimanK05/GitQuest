package com.gitquest.ui.campaign;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.gitquest.core.campaign.CampaignCatalog;
import com.gitquest.core.campaign.CampaignCatalog.ArcInfo;
import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.ui.sandbox.LanePalette;

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
 * Renders the Campaign skill tree as a single continuous commit chain —
 * levels ARE the commits, one lane color per arc/topic (the same
 * {@link LanePalette} used for branch lanes in Sandbox), connected
 * straight top-to-bottom so moving to the next topic reads as continuing
 * the same history rather than jumping to an unrelated node. Mirrors
 * {@code CommitGraphView}'s node/edge/ref-label conventions so Campaign and
 * Sandbox visually speak the same language, per CLAUDE.md's "Git becomes
 * visible" pitch.
 */
public final class SkillTreeView extends Pane {

    // Node column sits at the pane's horizontal midpoint (not just an
    // arbitrary left offset) so that when a parent StackPane centers this
    // whole Pane, the visible node chain — not just its bounding box —
    // actually lands in the middle: label text only extends rightward from
    // the node, so an off-center node position previously made the chain
    // look shifted left even though the Pane itself was centered.
    private static final double NODE_X = 200;
    private static final double MARGIN_Y = 50;
    private static final double ROW_HEIGHT = 110;
    private static final double RADIUS = 22;

    private static final Color LOCKED_FILL = Color.web("#3A3A3C");
    private static final Color STROKE = Color.web("#0C0C0D");
    private static final Color COMPLETED_STROKE = Color.web("#F05133");

    private final Group edgesLayer = new Group();
    private final Group nodesLayer = new Group();

    public SkillTreeView() {
        getStyleClass().add("commit-graph");
        getChildren().addAll(edgesLayer, nodesLayer);
    }

    public void render(List<ArcInfo> arcs, CampaignProgress progress, Consumer<LevelDefinition> onLevelSelected) {
        edgesLayer.getChildren().clear();
        nodesLayer.getChildren().clear();

        List<ChainEntry> chain = buildChain(arcs);

        double previousX = -1;
        double previousY = -1;
        String previousArcId = null;

        for (int i = 0; i < chain.size(); i++) {
            ChainEntry entry = chain.get(i);
            double y = MARGIN_Y + i * ROW_HEIGHT;
            // Levels gate strictly one-at-a-time (no skipping); placeholder
            // "coming soon" arc entries fall back to the coarser arc check
            // since they have no individual level to chain against.
            boolean unlocked = entry.level() != null
                    ? progress.isLevelUnlocked(entry.level().id())
                    : progress.isArcUnlocked(entry.arc().id());
            Color arcColor = LanePalette.forLane(entry.arcIndex());
            boolean startsNewArc = !entry.arc().id().equals(previousArcId);

            if (previousX >= 0) {
                Line edge = new Line(previousX, previousY, NODE_X, y);
                edge.setStroke(unlocked ? arcColor : LOCKED_FILL);
                edge.setStrokeWidth(2);
                edge.setOpacity(0.6);
                edgesLayer.getChildren().add(edge);
            }

            if (entry.level() == null) {
                addPlaceholderNode(NODE_X, y, entry.arc(), unlocked, startsNewArc, arcColor);
            } else {
                addLevelNode(NODE_X, y, entry.level(), entry.arc(), progress, unlocked, startsNewArc, arcColor, onLevelSelected);
            }

            previousX = NODE_X;
            previousY = y;
            previousArcId = entry.arc().id();
        }

        setPrefWidth(NODE_X * 2);
        setPrefHeight(MARGIN_Y + chain.size() * ROW_HEIGHT + 40);
    }

    /** One level per arc entry, or a single placeholder entry for an arc with no authored levels yet. */
    private record ChainEntry(ArcInfo arc, int arcIndex, LevelDefinition level) {
    }

    private static List<ChainEntry> buildChain(List<ArcInfo> arcs) {
        List<ChainEntry> chain = new ArrayList<>();
        for (int arcIndex = 0; arcIndex < arcs.size(); arcIndex++) {
            ArcInfo arc = arcs.get(arcIndex);
            List<LevelDefinition> levels = CampaignCatalog.levelsForArc(arc.id());
            if (levels.isEmpty()) {
                chain.add(new ChainEntry(arc, arcIndex, null));
            } else {
                for (LevelDefinition level : levels) {
                    chain.add(new ChainEntry(arc, arcIndex, level));
                }
            }
        }
        return chain;
    }

    private void addPlaceholderNode(double x, double y, ArcInfo arc, boolean unlocked, boolean startsNewArc, Color arcColor) {
        Circle circle = new Circle(x, y, RADIUS * 0.7);
        circle.setFill(LOCKED_FILL);
        circle.setStroke(STROKE);
        circle.setStrokeWidth(1.5);
        Tooltip.install(circle, new Tooltip(arc.title() + " — coming soon\n" + arc.description()));

        Label caption = new Label("🔒 coming soon");
        caption.setLayoutX(x + RADIUS + 10);
        caption.setLayoutY(y - 7);
        caption.setStyle("-fx-font-size: 11px; -fx-text-fill: #9DA5B4;");

        if (startsNewArc) {
            addArcLabel(x, y, arc, unlocked);
        }

        nodesLayer.getChildren().addAll(circle, caption);
    }

    private void addLevelNode(double x, double y, LevelDefinition level, ArcInfo arc, CampaignProgress progress,
            boolean unlocked, boolean startsNewArc, Color arcColor, Consumer<LevelDefinition> onLevelSelected) {
        boolean completed = progress.isLevelCompleted(level.id());

        Circle circle = new Circle(x, y, RADIUS);
        circle.setFill(!unlocked ? LOCKED_FILL : arcColor);
        circle.setStroke(completed ? COMPLETED_STROKE : STROKE);
        circle.setStrokeWidth(completed ? 3 : 1.5);
        Tooltip.install(circle, new Tooltip(level.title() + "\n"
                + (unlocked ? "Click for details" : "Locked")));

        if (unlocked) {
            circle.setCursor(Cursor.HAND);
            circle.setOnMouseClicked(e -> onLevelSelected.accept(level));
        }

        nodesLayer.getChildren().add(circle);

        // No play glyph here — clicking a node only selects it and opens the
        // detail tab; the tab's own Play button is what actually starts a
        // level. A checkmark still marks completion, and a lock still marks
        // an unreachable node, since those aren't action affordances. Added
        // after the circle so it draws on top, not underneath it.
        if (completed || !unlocked) {
            Text glyph = new Text(completed ? "✓" : "🔒");
            glyph.setStyle("-fx-font-size: 16px; -fx-fill: white;");
            glyph.setLayoutX(x - 6);
            glyph.setLayoutY(y + 6);
            glyph.setMouseTransparent(true);
            nodesLayer.getChildren().add(glyph);
        }

        Label title = new Label(level.title());
        title.setLayoutX(x + RADIUS + 10);
        title.setLayoutY(y - 7);
        title.setStyle("-fx-font-size: 12px; -fx-text-fill: #E6E6E6;");
        nodesLayer.getChildren().add(title);

        if (startsNewArc) {
            addArcLabel(x, y, arc, unlocked);
        }
    }

    /** A branch/ref-style tag marking where a new topic (arc) begins in the chain. */
    private void addArcLabel(double x, double y, ArcInfo arc, boolean unlocked) {
        Label arcLabel = new Label((unlocked ? "" : "🔒 ") + arc.title().toUpperCase());
        arcLabel.setLayoutX(x - RADIUS - 8);
        arcLabel.setLayoutY(y - RADIUS - 22);
        arcLabel.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: "
                + (unlocked ? "#F05133" : "#5B6472") + ";");
        nodesLayer.getChildren().add(arcLabel);
    }
}
