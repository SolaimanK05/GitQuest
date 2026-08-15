package com.gitquest.core.campaign;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once HEAD has reached at least {@code minimumCommits} commits. */
public record MinimumCommitCountGoal(int minimumCommits) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        return model.snapshot().commits().size() >= minimumCommits;
    }

    @Override
    public String describeObjective() {
        return "Reach at least " + minimumCommits + " commit" + (minimumCommits == 1 ? "" : "s") + ".";
    }
}
