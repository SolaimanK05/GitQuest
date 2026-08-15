package com.gitquest.core.campaign;

import java.util.List;

import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once two branches point at the exact same commit — e.g. after a fast-forward merge. */
public record RefsConvergedGoal(String branchA, String branchB) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        List<BranchRef> branches = model.snapshot().branches();
        BranchRef a = findByName(branches, branchA);
        BranchRef b = findByName(branches, branchB);
        return a != null && b != null && a.targetId().equals(b.targetId());
    }

    private static BranchRef findByName(List<BranchRef> branches, String name) {
        return branches.stream().filter(ref -> ref.name().equals(name)).findFirst().orElse(null);
    }

    @Override
    public String describeObjective() {
        return "Make " + branchA + " and " + branchB + " point at the same commit.";
    }
}
