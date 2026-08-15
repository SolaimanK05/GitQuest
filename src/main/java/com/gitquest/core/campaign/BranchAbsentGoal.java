package com.gitquest.core.campaign;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once no branch with the given name remains. */
public record BranchAbsentGoal(String branchName) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        return model.snapshot().branches().stream().noneMatch(ref -> ref.name().equals(branchName));
    }

    @Override
    public String describeObjective() {
        return "Delete the " + branchName + " branch.";
    }
}
