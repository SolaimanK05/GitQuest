package com.gitquest.ui.sandbox;

import java.util.List;

import com.gitquest.core.model.CommitNode;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
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

    private static final Color NORMAL_STROKE = Color.web("#1F3B73");
    private static final Color HEAD_STROKE = Color.web("#E08A00");

    private final Circle circle = new Circle(RADIUS, Color.web("#4C8BF5"));
    private final Label messageLabel = new Label();
    private final TextFlow refLabel = new TextFlow();

    private int lane;
    private int sequenceIndex;

    CommitNodeView(CommitNode commit) {
        circle.setStroke(NORMAL_STROKE);
        circle.setStrokeWidth(1.5);
        messageLabel.setStyle("-fx-font-size: 11px;");
        messageLabel.setMouseTransparent(true);
        refLabel.setMouseTransparent(true);
        update(commit);
    }

    void update(CommitNode commit) {
        this.lane = commit.lane();
        this.sequenceIndex = commit.sequenceIndex();
        messageLabel.setText(commit.shortMessage());
        Tooltip.install(circle, new Tooltip(commit.shortMessage() + "\n" + commit.authorName()));
    }

    /** Rebuilds the ref-name strip, bolding whichever ref matches the current branch. */
    void setRefNames(List<String> refNames, String currentBranchName) {
        refLabel.getChildren().clear();
        for (int i = 0; i < refNames.size(); i++) {
            String name = refNames.get(i);
            boolean isCurrent = name.equals(currentBranchName);
            Text text = new Text(name);
            text.setStyle(isCurrent
                    ? "-fx-font-weight: bold; -fx-fill: #1F8A3B; -fx-font-size: 11px;"
                    : "-fx-fill: #5B6472; -fx-font-size: 11px;");
            refLabel.getChildren().add(text);
            if (i < refNames.size() - 1) {
                refLabel.getChildren().add(new Text(", "));
            }
        }
    }

    /** Highlights this node's circle as the current HEAD commit (or reverts it). */
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
