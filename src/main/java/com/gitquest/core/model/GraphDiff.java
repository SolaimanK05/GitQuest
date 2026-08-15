package com.gitquest.core.model;

import java.util.List;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Describes what changed between two {@link RepoSnapshot}s, so the UI can
 * animate cause-and-effect (fade/slide/ease) instead of re-rendering the
 * whole graph statically. Produced by {@link GraphDiffCalculator}.
 */
public record GraphDiff(
        List<CommitNode> addedCommits,
        List<ObjectId> removedCommitIds,
        List<LaneShift> laneShifts,
        List<RefChange> refChanges,
        HeadChange headChange) {

    public boolean isEmpty() {
        return addedCommits.isEmpty()
                && removedCommitIds.isEmpty()
                && laneShifts.isEmpty()
                && refChanges.isEmpty()
                && headChange == null;
    }

    /** An existing commit moved from one lane to another (a branch/merge reshaped the graph). */
    public record LaneShift(ObjectId commitId, int oldLane, int newLane) {
    }

    /** A branch/tag ref was created, moved, or deleted. */
    public record RefChange(String refName, ObjectId oldTarget, ObjectId newTarget, RefChangeType type) {
    }

    public enum RefChangeType { ADDED, MOVED, REMOVED }

    /** HEAD moved to a different ref and/or commit (checkout, commit, merge, reset...). */
    public record HeadChange(String oldRefName, String newRefName, ObjectId oldCommitId, ObjectId newCommitId) {
    }
}
