package com.gitquest.core.campaign;

import org.eclipse.jgit.api.errors.GitAPIException;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once a conflicted merge has been completed by hand: no
 * conflicting files remain and a merge commit (2+ parents) exists.
 */
public record ConflictsResolvedGoal() implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        try {
            if (!model.getGit().status().call().getConflicting().isEmpty()) {
                return false;
            }
        } catch (GitAPIException e) {
            return false;
        }
        return model.snapshot().commits().stream().anyMatch(commit -> commit.parentIds().size() >= 2);
    }

    @Override
    public String describeObjective() {
        return "Resolve the conflict and complete the merge.";
    }
}
