package com.gitquest.core.campaign;

import java.io.IOException;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once a local branch named {@code branchName} exists and its {@code branch.<name>.remote} config points at {@code origin} — a real tracking relationship, not just a same-named branch. */
public record TrackingBranchGoal(String branchName) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        try {
            if (model.getRepository().findRef(branchName) == null) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        String remote = model.getRepository().getConfig().getString("branch", branchName, "remote");
        return "origin".equals(remote);
    }

    @Override
    public String describeObjective() {
        return "Create a local " + branchName + " branch that tracks origin/" + branchName + ".";
    }
}
