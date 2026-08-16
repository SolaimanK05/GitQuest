package com.gitquest.core.campaign;

import java.util.List;

import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once HEAD is a single-parent commit whose parent is exactly {@code upstreamBranch}'s current tip — a rebase replayed HEAD's commit(s) directly on top of it, rather than merging. */
public record LinearRebaseOntoGoal(String upstreamBranch) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        RepoSnapshot snapshot = model.snapshot();
        List<BranchRef> branches = snapshot.branches();
        BranchRef upstream = branches.stream().filter(b -> b.name().equals(upstreamBranch)).findFirst().orElse(null);
        if (upstream == null) {
            return false;
        }
        CommitNode head = snapshot.commits().stream()
                .filter(commit -> commit.id().equals(snapshot.headCommitId()))
                .findFirst().orElse(null);
        return head != null && head.parentIds().size() == 1 && head.parentIds().get(0).equals(upstream.targetId());
    }

    @Override
    public String describeObjective() {
        return "Rebase onto " + upstreamBranch + " so your commit sits directly on top of its tip.";
    }
}
