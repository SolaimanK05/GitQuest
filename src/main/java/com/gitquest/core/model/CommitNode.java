package com.gitquest.core.model;

import java.util.List;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Immutable snapshot of a single commit as laid out for the graph view.
 * {@code lane} and {@code sequenceIndex} come straight from JGit's
 * {@code PlotWalk}/{@code PlotCommitList} layout — never recomputed by hand.
 */
public record CommitNode(
        ObjectId id,
        String shortMessage,
        String authorName,
        long commitEpochSeconds,
        int lane,
        int sequenceIndex,
        List<ObjectId> parentIds,
        List<String> refNames) {
}
