package com.gitquest.core.command;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.GraphDiffCalculator;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;

/**
 * Runs Git operations through JGit's porcelain API and returns a
 * before/after {@link GraphDiff} so the UI can animate the change. Every
 * method here is synchronous/blocking (disk I/O) — callers must run these
 * off the JavaFX Application Thread (see {@code CommandService}).
 */
public final class CommandExecutor {

    private final RepoStateModel model;

    public CommandExecutor(RepoStateModel model) {
        this.model = model;
    }

    public GraphDiff stageAll() {
        return mutate(() -> git().add().addFilepattern(".").call());
    }

    public GraphDiff commit(String message, String authorName, String authorEmail) {
        return mutate(() -> git().commit()
                .setMessage(message)
                .setAuthor(new PersonIdent(authorName, authorEmail))
                .call());
    }

    public GraphDiff createBranch(String name) {
        return mutate(() -> git().branchCreate().setName(name).call());
    }

    public GraphDiff checkout(String refName) {
        return mutate(() -> git().checkout().setName(refName).call());
    }

    public GraphDiff merge(String branchName, boolean noFastForward) {
        return mutate(() -> {
            Repository repository = model.getRepository();
            Ref branchRef = repository.findRef(branchName);
            if (branchRef == null) {
                throw new GitCommandException("No such branch: " + branchName);
            }
            MergeResult result = git().merge()
                    .include(branchRef)
                    .setFastForward(noFastForward ? FastForwardMode.NO_FF : FastForwardMode.FF)
                    .call();
            if (!result.getMergeStatus().isSuccessful()) {
                throw new GitCommandException("Merge did not complete: " + result.getMergeStatus());
            }
        });
    }

    public StatusSnapshot status() {
        try {
            Status status = git().status().call();
            return new StatusSnapshot(
                    new HashSet<>(status.getAdded()),
                    new HashSet<>(status.getChanged()),
                    new HashSet<>(status.getModified()),
                    new HashSet<>(status.getRemoved()),
                    new HashSet<>(status.getMissing()),
                    new HashSet<>(status.getUntracked()),
                    new HashSet<>(status.getConflicting()));
        } catch (GitAPIException e) {
            throw new GitCommandException("Failed to read status", e);
        }
    }

    private Git git() {
        return model.getGit();
    }

    /** Shared shape for every mutating command: snapshot, run, refresh, snapshot, diff. */
    private GraphDiff mutate(GitCall call) {
        RepoSnapshot before = model.snapshot();
        try {
            call.run();
            model.refresh();
        } catch (GitCommandException e) {
            throw e;
        } catch (Exception e) {
            throw new GitCommandException("Git command failed: " + e.getMessage(), e);
        }
        RepoSnapshot after = model.snapshot();
        return GraphDiffCalculator.diff(before, after);
    }

    @FunctionalInterface
    private interface GitCall {
        void run() throws Exception;
    }
}
