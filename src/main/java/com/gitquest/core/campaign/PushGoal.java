package com.gitquest.core.campaign;

import java.io.File;
import java.net.URI;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once the local repo's own {@code origin} (its bare "remote", per CLAUDE.md 4.3 —
 * simulated locally) has a {@code main} matching the local one — a push actually uploaded the
 * local commit(s). Reads the origin's location straight from the local repo's own git config
 * ({@code remote.origin.url}), the same way a real remote would be discovered.
 */
public record PushGoal() implements GoalSpec {

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
                return localTip != null && localTip.equals(remoteTip);
            }
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String describeObjective() {
        return "Push your local commit to origin.";
    }
}
