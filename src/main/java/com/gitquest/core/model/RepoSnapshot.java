package com.gitquest.core.model;

import java.util.List;

import org.eclipse.jgit.lib.ObjectId;

/**
 * Immutable capture of {@link RepoStateModel}'s observable state at one
 * instant, taken before/after a command purely so {@link GraphDiffCalculator}
 * has two fixed points to diff. Distinct from the live observable model.
 */
public record RepoSnapshot(
        List<CommitNode> commits,
        List<BranchRef> branches,
        String headRefName,
        ObjectId headCommitId) {
}
