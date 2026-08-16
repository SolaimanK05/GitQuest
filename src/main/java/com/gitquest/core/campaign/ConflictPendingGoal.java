package com.gitquest.core.campaign;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once the working tree has at least one unresolved merge conflict. */
public record ConflictPendingGoal() implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        try {
            return !model.getGit().status().call().getConflicting().isEmpty();
        } catch (GitAPIException e) {
            return false;
        }
    }

    @Override
    public String describeObjective() {
        return "Trigger a merge conflict.";
    }
}
