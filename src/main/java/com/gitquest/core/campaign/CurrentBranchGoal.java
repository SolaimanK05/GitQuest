package com.gitquest.core.campaign;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once HEAD is on the given branch. */
public record CurrentBranchGoal(String branchName) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        return branchName.equals(model.snapshot().headRefName());
    }

    @Override
    public String describeObjective() {
        return "Switch to the " + branchName + " branch.";
    }
}
