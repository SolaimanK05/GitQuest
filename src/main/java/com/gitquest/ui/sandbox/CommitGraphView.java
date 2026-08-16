package com.gitquest.ui.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.GraphDiff.LaneShift;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
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

    private static final Color PENDING_MERGE_COLOR = Color.web("#F05133");

    private final Map<ObjectId, CommitNodeView> nodesById = new HashMap<>();
    private final Map<String, Line> edgesByKey = new HashMap<>();
    private final Group edgesLayer = new Group();
    private final Group nodesLayer = new Group();
    private ObjectId currentHeadCommitId;

    private Circle pendingMergeCircle;
    private Label pendingMergeLabel;
    private Line pendingMergeLineToOurs;
    private Line pendingMergeLineToTheirs;
    private Timeline pendingMergePulse;

    public CommitGraphView() {
        getStyleClass().add("commit-graph");
        getChildren().addAll(edgesLayer, nodesLayer);
    }

    /** First load after Open/Init/Clone: lay out with no animation. */
    public void renderInitial(List<CommitNode> commits) {
        nodesById.clear();
        edgesByKey.clear();
        edgesLayer.getChildren().clear();
        nodesLayer.getChildren().clear();
        currentHeadCommitId = null;
        pendingMergeCircle = null;
        pendingMergeLabel = null;
        pendingMergeLineToOurs = null;
        pendingMergeLineToTheirs = null;
        if (pendingMergePulse != null) {
            pendingMergePulse.stop();
            pendingMergePulse = null;
        }

        for (CommitNode commit : commits) {
            addNodeView(commit);
        }
        for (CommitNode commit : commits) {
            addEdgesFor(commit);
        }
        resizeToContent();
    }

    /**
     * Animate a before/after diff: fade+slide new commits in, ease lane shifts sideways, fade
     * removed commits out. A commit disappears from the graph when it's no longer reachable from
     * any ref (e.g. after "Undo Last Command"/a hard reset drops it from history) — without this,
     * its node and edges would linger forever since nothing else ever prunes them.
     */
    public void animateDiff(GraphDiff diff) {
        ParallelTransition all = new ParallelTransition();

        List<ObjectId> removalsToApply = new ArrayList<>();
        for (ObjectId removedId : diff.removedCommitIds()) {
            CommitNodeView nodeView = nodesById.get(removedId);
            if (nodeView != null) {
                all.getChildren().addAll(fadeOut(nodeView));
                removalsToApply.add(removedId);
            }
        }
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
                all.getChildren().addAll(slideToNewLane(nodeView, shift.oldLane(), shift.newLane()));
            }
        }

        all.setOnFinished(event -> {
            for (ObjectId removedId : removalsToApply) {
                removeNodeAndEdges(removedId);
            }
            resizeToContent();
        });
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

    /**
     * A conflicted merge is a pending state, not a failure (CLAUDE.md 4.3) — shown as a dashed,
     * gently pulsing placeholder at the would-be merge commit's position (one row below the later
     * of the two tips, centered between their lanes), with dashed lines back to both parents. Once
     * the merge completes (or is aborted), the caller stops calling this and the placeholder is
     * cleared — the real merge commit then fades in through the normal {@link #animateDiff} path.
     */
    public void showPendingMerge(CommitNode ours, CommitNode theirs) {
        double oursX = CommitNodeView.MARGIN_X + ours.lane() * CommitNodeView.LANE_WIDTH;
        double oursY = CommitNodeView.MARGIN_Y + ours.sequenceIndex() * CommitNodeView.ROW_HEIGHT;
        double theirsX = CommitNodeView.MARGIN_X + theirs.lane() * CommitNodeView.LANE_WIDTH;
        double theirsY = CommitNodeView.MARGIN_Y + theirs.sequenceIndex() * CommitNodeView.ROW_HEIGHT;
        int row = Math.max(ours.sequenceIndex(), theirs.sequenceIndex()) + 1;
        double x = (oursX + theirsX) / 2;
        double y = CommitNodeView.MARGIN_Y + row * CommitNodeView.ROW_HEIGHT;

        ensurePendingMergeNodesExist();

        pendingMergeCircle.setCenterX(x);
        pendingMergeCircle.setCenterY(y);
        pendingMergeLabel.setLayoutX(x + CommitNodeView.RADIUS + 6);
        pendingMergeLabel.setLayoutY(y - 8);
        pendingMergeLineToOurs.setStartX(oursX);
        pendingMergeLineToOurs.setStartY(oursY);
        pendingMergeLineToOurs.setEndX(x);
        pendingMergeLineToOurs.setEndY(y);
        pendingMergeLineToTheirs.setStartX(theirsX);
        pendingMergeLineToTheirs.setStartY(theirsY);
        pendingMergeLineToTheirs.setEndX(x);
        pendingMergeLineToTheirs.setEndY(y);

        setPrefWidth(Math.max(getPrefWidth(), x + 160));
        setPrefHeight(Math.max(getPrefHeight(), y + 80));
    }

    public void clearPendingMerge() {
        if (pendingMergeCircle == null) {
            return;
        }
        pendingMergePulse.stop();
        nodesLayer.getChildren().removeAll(pendingMergeCircle, pendingMergeLabel);
        edgesLayer.getChildren().removeAll(pendingMergeLineToOurs, pendingMergeLineToTheirs);
        pendingMergeCircle = null;
        pendingMergeLabel = null;
        pendingMergeLineToOurs = null;
        pendingMergeLineToTheirs = null;
        pendingMergePulse = null;
    }

    private void ensurePendingMergeNodesExist() {
        if (pendingMergeCircle != null) {
            return;
        }
        pendingMergeCircle = new Circle(CommitNodeView.RADIUS);
        pendingMergeCircle.setFill(Color.TRANSPARENT);
        pendingMergeCircle.setStroke(PENDING_MERGE_COLOR);
        pendingMergeCircle.setStrokeWidth(2);
        pendingMergeCircle.getStrokeDashArray().addAll(4.0, 4.0);

        pendingMergeLabel = new Label("merge pending…");
        pendingMergeLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #F05133; -fx-font-style: italic;");
        pendingMergeLabel.setMouseTransparent(true);

        pendingMergeLineToOurs = pendingMergeDashedLine();
        pendingMergeLineToTheirs = pendingMergeDashedLine();

        edgesLayer.getChildren().addAll(pendingMergeLineToOurs, pendingMergeLineToTheirs);
        nodesLayer.getChildren().addAll(pendingMergeCircle, pendingMergeLabel);

        pendingMergePulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(pendingMergeCircle.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(700), new KeyValue(pendingMergeCircle.opacityProperty(), 0.3)),
                new KeyFrame(Duration.millis(1400), new KeyValue(pendingMergeCircle.opacityProperty(), 1.0)));
        pendingMergePulse.setCycleCount(Animation.INDEFINITE);
        pendingMergePulse.play();
    }

    private static Line pendingMergeDashedLine() {
        Line line = new Line();
        line.setStroke(PENDING_MERGE_COLOR);
        line.setStrokeWidth(1.2);
        line.setOpacity(0.5);
        line.setMouseTransparent(true);
        line.getStrokeDashArray().addAll(3.0, 3.0);
        return line;
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

    private List<Animation> fadeOut(CommitNodeView nodeView) {
        FadeTransition fadeCircle = new FadeTransition(ANIMATION_DURATION, nodeView.getCircle());
        fadeCircle.setToValue(0);
        FadeTransition fadeLabel = new FadeTransition(ANIMATION_DURATION, nodeView.getMessageLabel());
        fadeLabel.setToValue(0);
        FadeTransition fadeRefs = new FadeTransition(ANIMATION_DURATION, nodeView.getRefLabel());
        fadeRefs.setToValue(0);
        return List.of(fadeCircle, fadeLabel, fadeRefs);
    }

    /** Physically removes a node (post fade-out) and every edge touching it — nothing else ever prunes stale nodes/edges. */
    private void removeNodeAndEdges(ObjectId commitId) {
        CommitNodeView nodeView = nodesById.remove(commitId);
        if (nodeView == null) {
            return;
        }
        nodesLayer.getChildren().removeAll(nodeView.getCircle(), nodeView.getMessageLabel(), nodeView.getRefLabel());
        if (commitId.equals(currentHeadCommitId)) {
            currentHeadCommitId = null;
        }

        List<String> staleKeys = new ArrayList<>();
        String idName = commitId.name();
        for (String key : edgesByKey.keySet()) {
            if (key.startsWith(idName + ">") || key.endsWith(">" + idName)) {
                staleKeys.add(key);
            }
        }
        for (String key : staleKeys) {
            edgesLayer.getChildren().remove(edgesByKey.remove(key));
        }
    }

    private List<Animation> slideToNewLane(CommitNodeView nodeView, int oldLane, int newLane) {
        double oldX = CommitNodeView.MARGIN_X + oldLane * CommitNodeView.LANE_WIDTH;
        // nodeView's cached lane (and thus targetX()) is still the OLD lane until this call —
        // it was only ever set once at construction/last update, never on a lane shift.
        nodeView.updateLane(newLane);
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
            if (edgesByKey.containsKey(key)) {
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
            edgesByKey.put(key, edge);
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
