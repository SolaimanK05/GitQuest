package com.gitquest.core.campaign;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/** Satisfied once local {@code main} matches {@code refs/remotes/origin/main} — pull (fetch + merge) actually brought the remote's new commits in. */
public record PullGoal() implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        Repository repository = model.getRepository();
        try {
            ObjectId remoteTip = repository.resolve("refs/remotes/origin/main");
            ObjectId localTip = repository.resolve("main");
            return remoteTip != null && remoteTip.equals(localTip);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String describeObjective() {
        return "Pull from origin — bring your branch up to date with the remote.";
    }
}
