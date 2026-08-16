package com.gitquest.core.campaign;

import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once HEAD carries exactly {@code expectedMessage} and the repo has exactly
 * {@code expectedCommitCount} commits total — the shared shape behind every "rewrite the last
 * commit" level (amend, reset --soft + recommit, or an interactive-rebase squash all produce a
 * different commit SHA but the same end state, so any of them satisfies this).
 */
public record HeadMessageAndCountGoal(String expectedMessage, int expectedCommitCount) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        RepoSnapshot snapshot = model.snapshot();
        if (snapshot.commits().size() != expectedCommitCount) {
            return false;
        }
        CommitNode head = snapshot.commits().stream()
                .filter(commit -> commit.id().equals(snapshot.headCommitId()))
                .findFirst().orElse(null);
        return head != null && expectedMessage.equals(head.shortMessage());
    }

    @Override
    public String describeObjective() {
        return "Rewrite history so HEAD reads \"" + expectedMessage + "\" and there are exactly "
                + expectedCommitCount + " commit(s).";
    }
}
