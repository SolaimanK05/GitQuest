package com.gitquest.core.validation;

import com.gitquest.core.model.RepoStateModel;

/**
 * Describes a target repo state (branch shape, HEAD position, file
 * contents...) for a Campaign level. Validated against end state, not an
 * exact command sequence — there are multiple valid ways to reach most Git
 * states. No level content lives here yet; this is the seam Campaign plugs
 * into later.
 */
public interface GoalSpec {

    boolean isSatisfied(RepoStateModel model);

    String describeObjective();
}
