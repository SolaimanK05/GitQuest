package com.gitquest.ui.sandbox;

import java.util.List;

import com.gitquest.core.model.CommitNode;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/**
 * One commit's visual: a circle, a short-message label, and a ref-name
 * strip (branch/tag names pointing at this commit) positioned by
 * (lane, sequenceIndex). The circle gets a highlighted ring when this
 * commit is HEAD; whichever ref name matches the current branch is bolded.
 */
final class CommitNodeView {

    static final double LANE_WIDTH = 70;
    static final double ROW_HEIGHT = 56;
    static final double MARGIN_X = 50;
    static final double MARGIN_Y = 40;
    static final double RADIUS = 10;

    /** Git's brand red — reserved for the HEAD ring, never used as a lane color. */
    private static final Color HEAD_STROKE = Color.web("#F05133");
    private static final Color NORMAL_STROKE = Color.web("#0C0C0D");

    private final Circle circle = new Circle(RADIUS);
    private final Label messageLabel = new Label();
    private final TextFlow refLabel = new TextFlow();

    private int lane;
    private int sequenceIndex;

    CommitNodeView(CommitNode commit) {
        circle.setStroke(NORMAL_STROKE);
        circle.setStrokeWidth(1.5);
        messageLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #E6E6E6;");
        messageLabel.setMouseTransparent(true);
        refLabel.setMouseTransparent(true);
        update(commit);
    }

    void update(CommitNode commit) {
        this.lane = commit.lane();
        this.sequenceIndex = commit.sequenceIndex();
        circle.setFill(LanePalette.forLane(commit.lane()));
        messageLabel.setText(commit.shortMessage());
        Tooltip.install(circle, new Tooltip(commit.shortMessage() + "\n" + commit.authorName()));
    }

    /** Rebuilds the ref-name strip: a lane-color chip, then names (bolding the current branch). */
    void setRefNames(List<String> refNames, String currentBranchName) {
        refLabel.getChildren().clear();
        if (refNames.isEmpty()) {
            return;
        }
        Rectangle chip = new Rectangle(8, 8, LanePalette.forLane(lane));
        chip.setArcWidth(2);
        chip.setArcHeight(2);
        chip.setTranslateY(2);
        refLabel.getChildren().add(chip);
        refLabel.getChildren().add(new Text(" "));
        for (int i = 0; i < refNames.size(); i++) {
            String name = refNames.get(i);
            boolean isCurrent = name.equals(currentBranchName);
            Text text = new Text(name);
            text.setStyle(isCurrent
                    ? "-fx-font-weight: bold; -fx-fill: #F05133; -fx-font-size: 11px;"
                    : "-fx-fill: #9DA5B4; -fx-font-size: 11px;");
            refLabel.getChildren().add(text);
            if (i < refNames.size() - 1) {
                refLabel.getChildren().add(new Text(", "));
            }
        }
    }

    /** Highlights this node's circle as the current HEAD commit (or reverts it), using Git's brand red. */
    void setHead(boolean isHead) {
        circle.setStroke(isHead ? HEAD_STROKE : NORMAL_STROKE);
        circle.setStrokeWidth(isHead ? 3 : 1.5);
    }

    double targetX() {
        return MARGIN_X + lane * LANE_WIDTH;
    }

    double targetY() {
        return MARGIN_Y + sequenceIndex * ROW_HEIGHT;
    }

    Circle getCircle() {
        return circle;
    }

    Label getMessageLabel() {
        return messageLabel;
    }

    TextFlow getRefLabel() {
        return refLabel;
    }
}
