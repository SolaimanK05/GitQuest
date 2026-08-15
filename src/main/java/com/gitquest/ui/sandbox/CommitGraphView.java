package com.gitquest.ui.sandbox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.GraphDiff.LaneShift;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

/**
 * Renders {@link CommitNode}s as circles positioned by (lane, sequenceIndex)
 * and animates {@link GraphDiff}s: new commits fade+slide into place, lane
 * shifts ease existing nodes sideways rather than snapping — per CLAUDE.md
 * Section 6. Parent-commit edges are bound reactively to each node's live
 * on-screen position, so they track any animation automatically.
 */
public final class CommitGraphView extends Pane {

    private static final Duration ANIMATION_DURATION = Duration.millis(420);

    private final Map<ObjectId, CommitNodeView> nodesById = new HashMap<>();
    private final Set<String> renderedEdgeKeys = new HashSet<>();
    private final Group edgesLayer = new Group();
    private final Group nodesLayer = new Group();
    private ObjectId currentHeadCommitId;

    public CommitGraphView() {
        getStyleClass().add("commit-graph");
        getChildren().addAll(edgesLayer, nodesLayer);
    }

    /** First load after Open/Init/Clone: lay out with no animation. */
    public void renderInitial(List<CommitNode> commits) {
        nodesById.clear();
        renderedEdgeKeys.clear();
        edgesLayer.getChildren().clear();
        nodesLayer.getChildren().clear();
        currentHeadCommitId = null;

        for (CommitNode commit : commits) {
            addNodeView(commit);
        }
        for (CommitNode commit : commits) {
            addEdgesFor(commit);
        }
        resizeToContent();
    }

    /** Animate a before/after diff: fade+slide new commits in, ease lane shifts sideways. */
    public void animateDiff(GraphDiff diff) {
        ParallelTransition all = new ParallelTransition();

        for (CommitNode added : diff.addedCommits()) {
            CommitNodeView nodeView = addNodeView(added);
            all.getChildren().addAll(fadeAndSlideIn(nodeView));
        }
        for (CommitNode added : diff.addedCommits()) {
            addEdgesFor(added);
        }
        for (LaneShift shift : diff.laneShifts()) {
            CommitNodeView nodeView = nodesById.get(shift.commitId());
            if (nodeView != null) {
                all.getChildren().addAll(slideToNewLane(nodeView, shift.oldLane()));
            }
        }

        all.setOnFinished(event -> resizeToContent());
        all.play();
    }

    /**
     * Re-syncs branch/tag ref labels (bolding whichever matches the current
     * branch) and the HEAD ring across all currently-rendered nodes. Cheap
     * and non-animated — call after every render/diff with the latest
     * snapshot, since ref moves (e.g. a new branch pointing at an existing,
     * already-rendered commit) aren't part of {@link GraphDiff#addedCommits()}.
     */
    public void syncRefsAndHead(List<CommitNode> commits, String headBranchName, ObjectId headCommitId) {
        for (CommitNode commit : commits) {
            CommitNodeView nodeView = nodesById.get(commit.id());
            if (nodeView != null) {
                nodeView.setRefNames(commit.refNames(), headBranchName);
            }
        }

        if (currentHeadCommitId != null) {
            CommitNodeView previousHead = nodesById.get(currentHeadCommitId);
            if (previousHead != null) {
                previousHead.setHead(false);
            }
        }
        currentHeadCommitId = headCommitId;
        CommitNodeView newHead = nodesById.get(headCommitId);
        if (newHead != null) {
            newHead.setHead(true);
        }
    }

    private CommitNodeView addNodeView(CommitNode commit) {
        CommitNodeView nodeView = new CommitNodeView(commit);
        Circle circle = nodeView.getCircle();
        Label messageLabel = nodeView.getMessageLabel();
        TextFlow refLabel = nodeView.getRefLabel();
        circle.setLayoutX(nodeView.targetX());
        circle.setLayoutY(nodeView.targetY());
        messageLabel.setLayoutX(nodeView.targetX() + CommitNodeView.RADIUS + 6);
        messageLabel.setLayoutY(nodeView.targetY() - 8);
        refLabel.setLayoutX(nodeView.targetX() + CommitNodeView.RADIUS + 6);
        refLabel.setLayoutY(nodeView.targetY() - 24);
        nodesById.put(commit.id(), nodeView);
        nodesLayer.getChildren().addAll(circle, messageLabel, refLabel);
        return nodeView;
    }

    private List<Animation> fadeAndSlideIn(CommitNodeView nodeView) {
        Circle circle = nodeView.getCircle();
        Label messageLabel = nodeView.getMessageLabel();
        TextFlow refLabel = nodeView.getRefLabel();
        circle.setOpacity(0);
        messageLabel.setOpacity(0);
        refLabel.setOpacity(0);
        circle.setTranslateY(-18);
        messageLabel.setTranslateY(-18);
        refLabel.setTranslateY(-18);

        FadeTransition fadeCircle = new FadeTransition(ANIMATION_DURATION, circle);
        fadeCircle.setToValue(1);
        FadeTransition fadeLabel = new FadeTransition(ANIMATION_DURATION, messageLabel);
        fadeLabel.setToValue(1);
        FadeTransition fadeRefs = new FadeTransition(ANIMATION_DURATION, refLabel);
        fadeRefs.setToValue(1);

        TranslateTransition slideCircle = new TranslateTransition(ANIMATION_DURATION, circle);
        slideCircle.setToY(0);
        slideCircle.setInterpolator(Interpolator.EASE_BOTH);
        TranslateTransition slideLabel = new TranslateTransition(ANIMATION_DURATION, messageLabel);
        slideLabel.setToY(0);
        slideLabel.setInterpolator(Interpolator.EASE_BOTH);
        TranslateTransition slideRefs = new TranslateTransition(ANIMATION_DURATION, refLabel);
        slideRefs.setToY(0);
        slideRefs.setInterpolator(Interpolator.EASE_BOTH);

        return List.of(fadeCircle, fadeLabel, fadeRefs, slideCircle, slideLabel, slideRefs);
    }

    private List<Animation> slideToNewLane(CommitNodeView nodeView, int oldLane) {
        double oldX = CommitNodeView.MARGIN_X + oldLane * CommitNodeView.LANE_WIDTH;
        double newX = nodeView.targetX();
        Circle circle = nodeView.getCircle();
        Label messageLabel = nodeView.getMessageLabel();
        TextFlow refLabel = nodeView.getRefLabel();
        circle.setLayoutX(newX);
        messageLabel.setLayoutX(newX + CommitNodeView.RADIUS + 6);
        refLabel.setLayoutX(newX + CommitNodeView.RADIUS + 6);
        circle.setTranslateX(oldX - newX);
        messageLabel.setTranslateX(oldX - newX);
        refLabel.setTranslateX(oldX - newX);

        TranslateTransition slideCircle = new TranslateTransition(ANIMATION_DURATION, circle);
        slideCircle.setToX(0);
        slideCircle.setInterpolator(Interpolator.EASE_BOTH);
        TranslateTransition slideLabel = new TranslateTransition(ANIMATION_DURATION, messageLabel);
        slideLabel.setToX(0);
        slideLabel.setInterpolator(Interpolator.EASE_BOTH);
        TranslateTransition slideRefs = new TranslateTransition(ANIMATION_DURATION, refLabel);
        slideRefs.setToX(0);
        slideRefs.setInterpolator(Interpolator.EASE_BOTH);

        return List.of(slideCircle, slideLabel, slideRefs);
    }

    private void addEdgesFor(CommitNode commit) {
        CommitNodeView child = nodesById.get(commit.id());
        if (child == null) {
            return;
        }
        for (ObjectId parentId : commit.parentIds()) {
            CommitNodeView parent = nodesById.get(parentId);
            if (parent == null) {
                continue;
            }
            String key = commit.id().name() + ">" + parentId.name();
            if (!renderedEdgeKeys.add(key)) {
                continue;
            }
            Line edge = new Line();
            edge.setStroke(LanePalette.forLane(commit.lane()));
            edge.setStrokeWidth(1.5);
            edge.setOpacity(0.55);
            edge.startXProperty().bind(child.getCircle().layoutXProperty().add(child.getCircle().translateXProperty()));
            edge.startYProperty().bind(child.getCircle().layoutYProperty().add(child.getCircle().translateYProperty()));
            edge.endXProperty().bind(parent.getCircle().layoutXProperty().add(parent.getCircle().translateXProperty()));
            edge.endYProperty().bind(parent.getCircle().layoutYProperty().add(parent.getCircle().translateYProperty()));
            edgesLayer.getChildren().add(edge);
        }
    }

    private void resizeToContent() {
        double maxX = 0;
        double maxY = 0;
        for (CommitNodeView nodeView : nodesById.values()) {
            maxX = Math.max(maxX, nodeView.targetX());
            maxY = Math.max(maxY, nodeView.targetY());
        }
        setPrefWidth(maxX + 160);
        setPrefHeight(maxY + 80);
    }
}
