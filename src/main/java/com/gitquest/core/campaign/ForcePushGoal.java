package com.gitquest.core.campaign;

import java.io.File;
import java.net.URI;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once origin's {@code main} matches local {@code main} <em>and</em> the commit that
 * used to be there ({@code overwrittenCommitMessage}) is no longer reachable from origin's new
 * tip — proof this happened via an actual overwrite (force-push), not a plain fast-forward push,
 * which real Git would have refused for this diverged-history scenario in the first place.
 */
public record ForcePushGoal(String overwrittenCommitMessage) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        Repository repository = model.getRepository();
        try {
            String originUrl = repository.getConfig().getString("remote", "origin", "url");
            if (originUrl == null) {
                return false;
            }
            ObjectId localTip = repository.resolve("main");
            try (Repository origin = new FileRepositoryBuilder().setGitDir(new File(URI.create(originUrl))).build()) {
                ObjectId remoteTip = origin.resolve("main");
                if (localTip == null || !localTip.equals(remoteTip)) {
                    return false;
                }
                try (RevWalk revWalk = new RevWalk(origin)) {
                    revWalk.markStart(revWalk.parseCommit(remoteTip));
                    for (RevCommit commit : revWalk) {
                        if (overwrittenCommitMessage.equals(commit.getShortMessage())) {
                            return false; // still reachable — nothing was actually overwritten
                        }
                    }
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String describeObjective() {
        return "Force-push to overwrite origin's diverged history with yours.";
    }
}
