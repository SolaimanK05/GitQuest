package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.RepoStateModel;

/**
 * Arc 6 content: fetch vs. pull, push, tracking branches, force-push dangers, per CLAUDE.md 4.2.
 * "No real network required" — every level's "origin" is a bare repo on the same disk (see
 * {@link #setUpOrigin}), the same trick the stretch-goal two-clone collaboration feature would use.
 */
final class RemotesLevels {

    private static final String AUTHOR_NAME = "GitQuest Campaign";
    private static final String AUTHOR_EMAIL = "campaign@gitquest.local";
    private static final PersonIdent TEAMMATE = new PersonIdent("Teammate", "teammate@gitquest.local");

    private RemotesLevels() {
    }

    static List<LevelDefinition> all() {
        return List.of(fetchFromOrigin(), pullFromOrigin(), pushToOrigin(), trackABranch(), forcePushDanger());
    }

    private static LevelDefinition fetchFromOrigin() {
        return new LevelDefinition(
                "remotes-fetch-from-origin",
                "remotes",
                "Fetch from Origin",
                "Git never reaches out to a remote on its own — fetch is how you ask. It downloads any "
                        + "new commits and updates your remote-tracking refs (origin/main) so you can see what "
                        + "changed, but it deliberately does not touch your own branch. Your working directory "
                        + "and your local main stay exactly as they were until you decide what to do with what "
                        + "you fetched.\n\n"
                        + "A teammate has pushed a new commit to origin that you don't have yet. Fetch it, and "
                        + "notice your own main doesn't move.",
                "Fetch-then-look-before-you-merge is what makes it safe to check in on a remote without risking your own work-in-progress.",
                List.of(),
                List.of(),
                (model, executor) -> setUpOrigin(model, executor, bareOrigin -> {
                    pushTeammateCommit(bareOrigin, "teammate.txt", "teammate's work\n", "Teammate's update");
                }),
                new FetchGoal("Shared base"));
    }

    private static LevelDefinition pullFromOrigin() {
        return new LevelDefinition(
                "remotes-pull-from-origin",
                "remotes",
                "Pull from Origin",
                "pull is fetch and merge in one step — it downloads what's new on the remote, same as "
                        + "fetch, and then immediately merges the tracked branch into your current one. Use it "
                        + "when you already know you want the remote's changes right now, rather than fetching "
                        + "first to look them over.\n\n"
                        + "Same situation as before — origin has a commit you don't. This time, pull it "
                        + "straight in.",
                "Reaching for fetch versus pull is a judgment call teams make constantly — pull when you trust it, fetch-then-review when you don't.",
                List.of(),
                List.of(),
                (model, executor) -> setUpOrigin(model, executor, bareOrigin -> {
                    pushTeammateCommit(bareOrigin, "teammate.txt", "teammate's work\n", "Teammate's update");
                }),
                new PullGoal());
    }

    private static LevelDefinition pushToOrigin() {
        return new LevelDefinition(
                "remotes-push-to-origin",
                "remotes",
                "Push to Origin",
                "push uploads commits your branch has that origin doesn't — the mirror image of fetch. As "
                        + "long as origin hasn't changed since you last synced with it, this is a simple "
                        + "fast-forward on the remote end: your commits just get appended to its history.\n\n"
                        + "You've committed new work locally that origin doesn't have yet. Push it.",
                "This is the step that actually shares your work — everything before it only ever happened on your own machine.",
                List.of(),
                List.of(),
                (model, executor) -> setUpOrigin(model, executor, null, () -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("my-work.txt"), "new local work\n");
                    executor.stageAll();
                    executor.commit("My new work", AUTHOR_NAME, AUTHOR_EMAIL);
                }),
                new PushGoal());
    }

    private static LevelDefinition trackABranch() {
        return new LevelDefinition(
                "remotes-track-a-branch",
                "remotes",
                "Track a Remote Branch",
                "A tracking branch is a local branch that remembers which remote branch it corresponds "
                        + "to — that's what lets plain fetch/pull/push work without spelling out the remote and "
                        + "branch name every time. Checking out a remote branch for the first time doesn't "
                        + "create this relationship by itself; you set it up explicitly with checkout -b.\n\n"
                        + "origin has a feature branch you don't have locally yet. Create a local feature "
                        + "branch that tracks it.",
                "Every branch you've pulled or pushed so far in this arc relied on main already being a tracking branch from the very first level — now you're setting that relationship up yourself.",
                List.of(),
                List.of(),
                (model, executor) -> setUpOrigin(model, executor, bareOrigin -> {
                    Path otherClone = Files.createTempDirectory("gitquest-teammate-");
                    try (Git other = Git.cloneRepository().setURI(bareOrigin.toUri().toString()).setDirectory(otherClone.toFile()).call()) {
                        other.checkout().setCreateBranch(true).setName("feature").call();
                        Files.writeString(otherClone.resolve("feature.txt"), "new feature\n");
                        other.add().addFilepattern(".").call();
                        other.commit().setMessage("Feature work").setAuthor(TEAMMATE).call();
                        other.push().add("feature").call();
                    }
                    model.getGit().fetch().setRemote("origin").call();
                }),
                new TrackingBranchGoal("feature"));
    }

    private static LevelDefinition forcePushDanger() {
        return new LevelDefinition(
                "remotes-force-push-danger",
                "remotes",
                "Force-Push Danger",
                "When your branch and origin have diverged — origin has commits you don't, and you have "
                        + "commits (or rewritten history) it doesn't — a plain push is rejected outright. Git "
                        + "refuses to silently drop commits from the remote. --force overrides that refusal and "
                        + "makes origin match your branch exactly, no matter what it had before. If someone "
                        + "else's work was sitting on that remote branch, force-pushing throws it away for "
                        + "everyone, not just you — there's no local-only undo once it's gone from the shared "
                        + "remote.\n\n"
                        + "Your local main has been rewritten (amended) since you last synced, and a teammate "
                        + "has separately pushed their own commit to origin in the meantime. Try to push — see "
                        + "it get rejected — then force-push to make origin match your history.",
                "This is the scenario every \"never force-push to a shared branch\" warning is about — you're about to feel exactly why.",
                List.of(),
                List.of(),
                (model, executor) -> setUpOrigin(model, executor, bareOrigin -> {
                    pushTeammateCommit(bareOrigin, "teammate.txt", "teammate's work\n", "Teammate's diverging work");
                    executor.amend("Shared base (rewritten locally)");
                }),
                new ForcePushGoal("Teammate's diverging work"));
    }

    // ---- shared remote-simulation setup ----

    @FunctionalInterface
    private interface OriginSeeder {
        void seed(Path bareOriginDir) throws Exception;
    }

    @FunctionalInterface
    private interface LocalFollowUp {
        void run() throws Exception;
    }

    private static void setUpOrigin(RepoStateModel model, CommandExecutor executor, OriginSeeder afterTrackingEstablished)
            throws Exception {
        setUpOrigin(model, executor, afterTrackingEstablished, null);
    }

    /**
     * Builds the shared starting point for every Arc 6 level: a local repo with one commit, wired
     * to a fresh local bare "origin" (CLAUDE.md 4.3 — no real network) with matching tracking
     * config, exactly as a real {@code git clone} would leave it. {@code afterTrackingEstablished}
     * (if given) seeds origin with further history <em>after</em> tracking is set up, simulating a
     * teammate pushing independently — so the local repo's remote-tracking ref stays stale until
     * the player fetches/pulls. {@code localFollowUp} (if given) then makes further local-only
     * changes, e.g. a commit only the player has.
     */
    private static void setUpOrigin(RepoStateModel model, CommandExecutor executor,
            OriginSeeder afterTrackingEstablished, LocalFollowUp localFollowUp) throws Exception {
        Path workTree = model.getRepository().getWorkTree().toPath();
        Files.writeString(workTree.resolve("README.md"), "A shared project.\n");
        executor.stageAll();
        executor.commit("Shared base", AUTHOR_NAME, AUTHOR_EMAIL);

        Path bareOrigin = Files.createTempDirectory("gitquest-origin-");
        try (Git bareGit = Git.init().setDirectory(bareOrigin.toFile()).setBare(true).call()) {
            // just creating it; nothing else to do with this handle
        }
        model.getGit().push().setRemote(bareOrigin.toUri().toString()).add("main").call();
        model.getGit().remoteAdd().setName("origin").setUri(new URIish(bareOrigin.toUri().toString())).call();
        model.getGit().fetch().setRemote("origin").call();
        StoredConfig config = model.getRepository().getConfig();
        config.setString("branch", "main", "remote", "origin");
        config.setString("branch", "main", "merge", "refs/heads/main");
        config.save();

        if (afterTrackingEstablished != null) {
            afterTrackingEstablished.seed(bareOrigin);
        }
        if (localFollowUp != null) {
            localFollowUp.run();
        }
        model.refresh();
    }

    /** Simulates a teammate independently cloning origin, committing, and pushing — without touching the player's local repo. */
    private static void pushTeammateCommit(Path bareOrigin, String fileName, String content, String message) throws Exception {
        Path otherClone = Files.createTempDirectory("gitquest-teammate-");
        try (Git other = Git.cloneRepository().setURI(bareOrigin.toUri().toString()).setDirectory(otherClone.toFile()).call()) {
            Files.writeString(otherClone.resolve(fileName), content);
            other.add().addFilepattern(".").call();
            other.commit().setMessage(message).setAuthor(TEAMMATE).call();
            other.push().call();
        }
    }
}
