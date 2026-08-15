package com.gitquest.core.validation;

import com.gitquest.core.model.RepoStateModel;

/** Checks whether the current repo state satisfies a {@link GoalSpec}. */
public final class GoalValidator {

    public boolean validate(GoalSpec spec, RepoStateModel model) {
        return spec.isSatisfied(model);
    }
}
