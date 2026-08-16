package com.gitquest.core.campaign;

import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once some branch (any name) points at a commit carrying {@code lostCommitMessage}.
 * Before recovery that commit is unreachable from every remaining ref, so it doesn't even appear
 * in {@link RepoStateModel#snapshot()} — the moment a new branch is created at it, it becomes
 * reachable again and this goal can find it.
 */
public record BranchRecoveredGoal(String lostCommitMessage) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        RepoSnapshot snapshot = model.snapshot();
        for (BranchRef branch : snapshot.branches()) {
            CommitNode target = snapshot.commits().stream()
                    .filter(commit -> commit.id().equals(branch.targetId()))
                    .findFirst().orElse(null);
            if (target != null && lostCommitMessage.equals(target.shortMessage())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String describeObjective() {
        return "Recover the deleted branch — recreate a branch pointing at the commit \"" + lostCommitMessage + "\".";
    }
}
