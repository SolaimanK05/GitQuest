package com.gitquest.ui.home;

import java.util.List;

import com.gitquest.core.campaign.CampaignCatalog;
import com.gitquest.core.campaign.CampaignCatalog.ArcInfo;
import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.ui.sandbox.LanePalette;

import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.TextAlignment;

/**
 * A compact, glanceable preview of Campaign progress for the Home screen
 * (CLAUDE.md 4.4: "campaign progress (skill-tree thumbnail)") — one small
 * node per arc, connected in a horizontal chain, using the same
 * {@link LanePalette} lane colors {@code SkillTreeView}/{@code
 * CommitGraphView} use elsewhere. Deliberately not the full detailed tree:
 * this is a summary a returning user reads in a glance, not a navigable
 * level picker — clicking it (or "Guided Campaign") opens the real thing.
 */
public final class SkillTreeThumbnail extends Pane {

    private static final double NODE_Y = 26;
    private static final double MARGIN_X = 26;
    private static final double COLUMN_WIDTH = 64;
    private static final double RADIUS = 12;
    private static final double SUMMARY_Y = 58;

    private static final Color LOCKED_FILL = Color.web("#3A3A3C");
    private static final Color STROKE = Color.web("#0C0C0D");

    public void render(CampaignProgress progress) {
        getChildren().clear();
        List<ArcInfo> arcs = CampaignCatalog.arcs();

        double previousX = -1;
        for (int i = 0; i < arcs.size(); i++) {
            ArcInfo arc = arcs.get(i);
            double x = MARGIN_X + i * COLUMN_WIDTH;
            boolean unlocked = progress.isArcUnlocked(arc.id());
            Color arcColor = LanePalette.forLane(i);

            if (previousX >= 0) {
                Line edge = new Line(previousX, NODE_Y, x, NODE_Y);
                edge.setStroke(unlocked ? arcColor : LOCKED_FILL);
                edge.setStrokeWidth(2);
                edge.setOpacity(0.6);
                getChildren().add(edge);
            }

            getChildren().add(arcNode(x, arc, i, unlocked, arcColor, progress));
            previousX = x;
        }

        double width = MARGIN_X * 2 + (arcs.size() - 1) * COLUMN_WIDTH;

        int total = CampaignCatalog.totalLevelCount();
        Label summary = new Label(progress.completedCount() + " / " + total + " levels complete");
        summary.getStyleClass().add("sub-label");
        summary.setLayoutY(SUMMARY_Y);
        summary.setPrefWidth(width);
        summary.setAlignment(javafx.geometry.Pos.CENTER);
        summary.setTextAlignment(TextAlignment.CENTER);
        getChildren().add(summary);

        setPrefWidth(width);
        setPrefHeight(SUMMARY_Y + 22);
    }

    private Circle arcNode(double x, ArcInfo arc, int arcIndex, boolean unlocked, Color arcColor, CampaignProgress progress) {
        List<LevelDefinition> levels = CampaignCatalog.levelsForArc(arc.id());
        long completedCount = levels.stream().filter(level -> progress.isLevelCompleted(level.id())).count();
        boolean allCompleted = !levels.isEmpty() && completedCount == levels.size();
        boolean started = completedCount > 0;

        Circle circle = new Circle(x, NODE_Y, RADIUS);
        circle.setStroke(STROKE);
        circle.setStrokeWidth(1.5);
        if (!unlocked) {
            circle.setFill(LOCKED_FILL);
        } else if (allCompleted) {
            circle.setFill(arcColor);
        } else if (started) {
            circle.setFill(arcColor.deriveColor(0, 1, 1, 0.45));
        } else {
            circle.setFill(Color.TRANSPARENT);
            circle.setStroke(arcColor);
            circle.setStrokeWidth(2);
        }

        String status = !unlocked ? "Locked" : (levels.isEmpty() ? "Coming soon" : completedCount + "/" + levels.size() + " levels complete");
        Tooltip.install(circle, new Tooltip(arc.title() + ": " + status));
        return circle;
    }

    public SkillTreeThumbnail() {
        setCursor(Cursor.HAND);
        Tooltip.install(this, new Tooltip("Your Guided Campaign progress. Click to continue."));
        // A bare Pane inside a VBox otherwise gets stretched to the VBox's full width (VBox
        // defaults fillWidth to true) -- since this draws its content anchored to its own local
        // origin, that stretch would leave it looking left-aligned instead of centered.
        setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
    }
}
