package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Arc 2 content: create/switch/delete branches, fast-forward vs. no-ff merge, per CLAUDE.md 4.2. */
final class BranchingLevels {

    private static final String AUTHOR_NAME = "GitQuest Campaign";
    private static final String AUTHOR_EMAIL = "campaign@gitquest.local";

    private BranchingLevels() {
    }

    static List<LevelDefinition> all() {
        return List.of(branchOut(), fastForwardMerge(), noFastForwardMerge(), deleteABranch());
    }

    private static LevelDefinition branchOut() {
        return new LevelDefinition(
                "branching-branch-out",
                "branching",
                "Branch Out",
                "A branch is just a movable, named pointer to a commit — creating one is instant and cheap, "
                        + "because it doesn't copy any files, it only adds a new pointer. Switching branches "
                        + "(checkout) then moves your working directory to match whatever that pointer points "
                        + "to. This is what lets you work on something new without touching your main line of "
                        + "work at all.\n\n"
                        + "Create a new branch called feature and switch to it.",
                "Branches are how Git lets many people (or many ideas) develop in parallel without stepping on each other.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new CurrentBranchGoal("feature"));
    }

    private static LevelDefinition fastForwardMerge() {
        return new LevelDefinition(
                "branching-fast-forward-merge",
                "branching",
                "Fast-Forward Merge",
                "When the branch you're merging INTO hasn't moved since you branched off, Git doesn't need to "
                        + "combine two separate histories — it can just slide that branch's pointer forward to "
                        + "match the tip of the branch you're merging in. This is a fast-forward merge, and it's "
                        + "why merging is often instant: there's nothing to actually merge, only a pointer to "
                        + "move.\n\n"
                        + "You're on main. The feature branch has one commit main doesn't have yet. Merge "
                        + "feature into main.",
                "Fast-forward is the default and simplest case — recognizing it is what makes the no-ff level right after this one make sense.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(workTree.resolve("feature.txt"), "New feature work.\n");
                    executor.stageAll();
                    executor.commit("Add feature work", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                },
                new RefsConvergedGoal("main", "feature"));
    }

    private static LevelDefinition noFastForwardMerge() {
        return new LevelDefinition(
                "branching-no-fast-forward-merge",
                "branching",
                "No-Fast-Forward Merge",
                "A fast-forward merge is convenient, but it also erases the fact that this work ever happened "
                        + "on a separate branch — history just looks like a straight line, as if you'd been "
                        + "committing directly on main the whole time. Passing --no-ff forces Git to create a "
                        + "real merge commit (one with two parents) even when a fast-forward would have worked, "
                        + "preserving a visible record that a branch existed and was merged in. Many teams "
                        + "require this so their history clearly shows feature branches instead of a flattened "
                        + "line of commits.\n\n"
                        + "Same setup as before — main hasn't moved, feature has one commit ahead. This time, "
                        + "force a real merge commit.",
                "This is exactly the merge-commit shape you already saw in the Sandbox tutorial — now you're making one on purpose.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(workTree.resolve("feature.txt"), "New feature work.\n");
                    executor.stageAll();
                    executor.commit("Add feature work", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                },
                new MergeCommitExistsGoal());
    }

    private static LevelDefinition deleteABranch() {
        return new LevelDefinition(
                "branching-delete-a-branch",
                "branching",
                "Delete a Branch",
                "Once a branch's work has been fully merged, keeping its name around just adds clutter — every "
                        + "commit it pointed to is already reachable from main, so deleting the branch loses "
                        + "nothing. Git also protects you here: deleting a branch refuses if it has commits main "
                        + "doesn't have yet, so you can't accidentally throw away unmerged work this way.\n\n"
                        + "The feature branch below is already fully merged into main. Delete it.",
                "Cleaning up merged branches is routine hygiene — real repositories accumulate dozens of stale ones if nobody bothers.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(workTree.resolve("feature.txt"), "New feature work.\n");
                    executor.stageAll();
                    executor.commit("Add feature work", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                    executor.merge("feature", false);
                },
                new BranchAbsentGoal("feature"));
    }
}
