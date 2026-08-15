package com.gitquest.core.campaign;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once any commit in history has 2+ parents — a real merge commit, not a fast-forward. */
public record MergeCommitExistsGoal() implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        return model.snapshot().commits().stream().anyMatch(commit -> commit.parentIds().size() >= 2);
    }

    @Override
    public String describeObjective() {
        return "Create a merge commit.";
    }
}
