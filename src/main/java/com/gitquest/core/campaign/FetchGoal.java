package com.gitquest.core.campaign;

import java.io.IOException;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once {@code refs/remotes/origin/main} has moved past local {@code main} while local
 * {@code main} itself stays put — the whole point of fetch vs. pull: fetch only updates what Git
 * knows about the remote, it never touches your own branch.
 */
public record FetchGoal(String expectedLocalHeadMessage) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        Repository repository = model.getRepository();
        try {
            ObjectId remoteTip = repository.resolve("refs/remotes/origin/main");
            ObjectId localTip = repository.resolve("main");
            if (remoteTip == null || localTip == null || remoteTip.equals(localTip)) {
                return false;
            }
            try (RevWalk revWalk = new RevWalk(repository)) {
                RevCommit localCommit = revWalk.parseCommit(localTip);
                return expectedLocalHeadMessage.equals(localCommit.getShortMessage());
            }
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String describeObjective() {
        return "Fetch from origin — update your knowledge of it without moving your own branch.";
    }
}
