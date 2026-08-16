package com.gitquest.core.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.CherryPickResult;
import org.eclipse.jgit.api.CherryPickResult.CherryPickStatus;
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.RebaseCommand.InteractiveHandler;
import org.eclipse.jgit.api.RebaseCommand.Operation;
import org.eclipse.jgit.api.RebaseResult;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.RevertCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;

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

    /** Creates a branch pointing at an arbitrary commit/ref rather than HEAD — how a deleted branch is recovered via reflog. */
    public GraphDiff createBranchAt(String name, String startPoint) {
        return mutate(() -> git().branchCreate().setName(name).setStartPoint(startPoint).call());
    }

    public GraphDiff checkout(String refName) {
        return mutate(() -> git().checkout().setName(refName).call());
    }

    /** {@code checkout -b <local> <remote-ref>} — explicitly creates a local branch tracking a remote one (Arc 6). */
    public GraphDiff checkoutNewTrackingBranch(String localName, String startPoint) {
        return mutate(() -> git().checkout()
                .setCreateBranch(true)
                .setName(localName)
                .setStartPoint(startPoint)
                .setUpstreamMode(SetupUpstreamMode.TRACK)
                .call());
    }

    /**
     * Deletes a branch (not force — JGit's {@code branchDelete} refuses, via
     * {@code NotMergedException}, if the branch has commits not reachable
     * from HEAD, same as real {@code git branch -d}). That refusal surfaces
     * as a normal {@link GitCommandException}, which is itself the teaching
     * moment: Git won't let you casually lose unmerged work.
     */
    public GraphDiff deleteBranch(String name) {
        return mutate(() -> git().branchDelete().setBranchNames(name).call());
    }

    /**
     * A {@code CONFLICTING} result is deliberately not treated as a failure
     * here — per CLAUDE.md 4.3, a conflicted merge is a "pending" state the
     * user resolves (see Arc 3), not an error. Only genuinely unsuccessful
     * statuses (e.g. {@code FAILED}, {@code CHECKOUT_CONFLICT}) throw.
     */
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
            MergeStatus status = result.getMergeStatus();
            if (!status.isSuccessful() && status != MergeStatus.CONFLICTING) {
                throw new GitCommandException("Merge did not complete: " + status);
            }
        });
    }

    /**
     * Cancels an in-progress conflicted merge, mirroring {@code git merge
     * --abort}: hard-resets the working tree/index back to HEAD (which a
     * conflicting non-fast-forward merge never moved) and clears the
     * {@code MERGE_HEAD}/{@code MERGE_MSG} state JGit's own
     * {@link org.eclipse.jgit.api.CommitCommand} would otherwise pick up on
     * the next commit.
     */
    public GraphDiff abortMerge() {
        return mutate(() -> {
            git().reset().setMode(ResetType.HARD).call();
            Repository repository = model.getRepository();
            repository.writeMergeHeads(null);
            repository.writeMergeCommitMsg(null);
        });
    }

    // ---- Arc 4: Rewriting History ----

    /** Replaces HEAD's commit with a new one carrying {@code newMessage} and whatever's currently staged. */
    public GraphDiff amend(String newMessage) {
        return mutate(() -> git().commit().setAmend(true).setMessage(newMessage).call());
    }

    /**
     * Replays a single existing commit's changes onto the current branch as a brand new commit —
     * a {@code CONFLICTING} result is a pending state to resolve (same rationale as {@link #merge}),
     * not a failure.
     */
    public GraphDiff cherryPick(String commitIdOrRef) {
        return mutate(() -> {
            RevCommit target = resolveCommit(commitIdOrRef);
            CherryPickResult result = git().cherryPick().include(target).call();
            CherryPickStatus status = result.getStatus();
            if (status != CherryPickStatus.OK && status != CherryPickStatus.CONFLICTING) {
                throw new GitCommandException("Cherry-pick did not complete: " + status);
            }
        });
    }

    /** Replays the current branch's commits on top of {@code upstream} instead of merging the two histories. */
    public GraphDiff rebase(String upstream) {
        return mutate(() -> {
            RevCommit target = resolveCommit(upstream);
            RebaseResult result = git().rebase().setUpstream(target).call();
            requireAcceptableRebaseStatus(result.getStatus());
        });
    }

    /**
     * Squashes every commit since diverging from {@code upstream} into one, carrying
     * {@code finalMessage} — a simplified stand-in for interactive rebase's squash use case (no
     * built-in editor to show a real todo list, per CLAUDE.md 4.3's "no built-in editor" scope).
     */
    public GraphDiff rebaseSquash(String upstream, String finalMessage) {
        return mutate(() -> {
            RevCommit target = resolveCommit(upstream);
            InteractiveHandler handler = new InteractiveHandler() {
                @Override
                public void prepareSteps(List<RebaseTodoLine> steps) {
                    for (int i = 1; i < steps.size(); i++) {
                        try {
                            steps.get(i).setAction(RebaseTodoLine.Action.SQUASH);
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }

                @Override
                public String modifyCommitMessage(String commit) {
                    return finalMessage;
                }
            };
            RebaseResult result = git().rebase().setUpstream(target).runInteractively(handler).call();
            requireAcceptableRebaseStatus(result.getStatus());
        });
    }

    public GraphDiff rebaseContinue() {
        return mutate(() -> {
            RebaseResult result = git().rebase().setOperation(Operation.CONTINUE).call();
            requireAcceptableRebaseStatus(result.getStatus());
        });
    }

    public GraphDiff rebaseAbort() {
        return mutate(() -> git().rebase().setOperation(Operation.ABORT).call());
    }

    private static void requireAcceptableRebaseStatus(RebaseResult.Status status) {
        boolean acceptable = status == RebaseResult.Status.OK
                || status == RebaseResult.Status.FAST_FORWARD
                || status == RebaseResult.Status.UP_TO_DATE
                || status == RebaseResult.Status.NOTHING_TO_COMMIT
                || status == RebaseResult.Status.STOPPED; // a real conflict mid-rebase — pending, not a failure
        if (!acceptable) {
            throw new GitCommandException("Rebase did not complete: " + status);
        }
    }

    // ---- Arc 5: Recovery ----

    /**
     * Moves the current branch to {@code ref}. {@code SOFT} leaves the index/working tree
     * untouched (only HEAD moves — everything since {@code ref} shows back up as staged changes),
     * {@code MIXED} also resets the index (unstaged changes), {@code HARD} resets everything,
     * discarding those commits' changes entirely.
     */
    public GraphDiff reset(String ref, ResetType mode) {
        return mutate(() -> git().reset().setMode(mode).setRef(ref).call());
    }

    /**
     * Undoes a commit's changes with a brand new commit rather than rewriting history — safe on
     * shared/pushed branches, unlike {@link #reset}. A {@code CONFLICTING} result (the commit
     * being undone touches lines since changed again) is a pending state to resolve.
     */
    public GraphDiff revert(String commitIdOrRef) {
        return mutate(() -> {
            RevCommit target = resolveCommit(commitIdOrRef);
            RevertCommand revertCommand = git().revert().include(target);
            RevCommit result = revertCommand.call();
            if (result == null) {
                MergeResult failing = revertCommand.getFailingResult();
                boolean conflicting = failing != null && failing.getMergeStatus() == MergeStatus.CONFLICTING;
                if (!conflicting) {
                    throw new GitCommandException("Revert did not complete"
                            + (failing != null ? ": " + failing.getMergeStatus() : ""));
                }
            }
        });
    }

    /**
     * Read-only history of where HEAD has pointed, newest first — real git's safety net for
     * recovering commits/branches nothing else can reach anymore. Not a {@link #mutate} call:
     * reading the reflog doesn't change repository state.
     */
    public List<String> reflog() {
        try {
            List<ReflogEntry> entries = model.getRepository().getRefDatabase().getReflogReader("HEAD").getReverseEntries();
            List<String> lines = new ArrayList<>();
            for (ReflogEntry entry : entries) {
                lines.add(entry.getNewId().abbreviate(7).name() + "  " + entry.getComment());
            }
            return lines;
        } catch (IOException e) {
            throw new GitCommandException("Failed to read reflog", e);
        }
    }

    // ---- Arc 6: Remotes ----

    /** Downloads new commits/refs from {@code origin} — updates remote-tracking refs only, never the local branch itself. */
    public GraphDiff fetch() {
        return mutate(() -> git().fetch().call());
    }

    /** Fetch, then merge the tracked remote branch into the current one — a {@code CONFLICTING} merge is pending, not a failure. */
    public GraphDiff pull() {
        return mutate(() -> {
            PullResult result = git().pull().call();
            boolean mergeConflicting = result.getMergeResult() != null
                    && result.getMergeResult().getMergeStatus() == MergeStatus.CONFLICTING;
            if (!result.isSuccessful() && !mergeConflicting) {
                throw new GitCommandException("Pull did not complete: " + result);
            }
        });
    }

    /**
     * Uploads local commits to {@code origin}. Without {@code force}, JGit (like real git) rejects
     * a non-fast-forward update instead of silently overwriting whatever origin has that this
     * branch doesn't — that rejection surfaces as a normal {@link GitCommandException}, which is
     * itself the Arc 6 "force-push danger" teaching moment.
     */
    public GraphDiff push(boolean force) {
        return mutate(() -> {
            Iterable<PushResult> results = git().push().setForce(force).call();
            for (PushResult result : results) {
                for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                    RemoteRefUpdate.Status status = update.getStatus();
                    if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                        throw new GitCommandException("Push rejected (" + status + ") — origin has commits you don't have. "
                                + "Fetch and merge/rebase first, or push --force if you really mean to overwrite it.");
                    }
                }
            }
        });
    }

    private RevCommit resolveCommit(String refOrSha) throws IOException {
        ObjectId id = model.getRepository().resolve(refOrSha);
        if (id == null) {
            throw new GitCommandException("No such commit or ref: " + refOrSha);
        }
        try (RevWalk walk = new RevWalk(model.getRepository())) {
            return walk.parseCommit(id);
        }
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
