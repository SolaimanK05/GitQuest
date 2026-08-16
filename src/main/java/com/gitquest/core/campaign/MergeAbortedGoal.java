package com.gitquest.core.campaign;

import java.util.List;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once a conflicted merge has been cleanly abandoned: no merge is
 * in progress, no conflicting files remain, and HEAD is still a single-parent
 * commit (i.e. no merge commit was created instead of aborting).
 */
public record MergeAbortedGoal() implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        try {
            List<ObjectId> mergeHeads = model.getRepository().readMergeHeads();
            if (mergeHeads != null && !mergeHeads.isEmpty()) {
                return false;
            }
            if (!model.getGit().status().call().getConflicting().isEmpty()) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        ObjectId head = model.snapshot().headCommitId();
        CommitNode headCommit = model.snapshot().commits().stream()
                .filter(commit -> commit.id().equals(head))
                .findFirst().orElse(null);
        return headCommit != null && headCommit.parentIds().size() < 2;
    }

    @Override
    public String describeObjective() {
        return "Abort the merge and return to a clean state.";
    }
}
