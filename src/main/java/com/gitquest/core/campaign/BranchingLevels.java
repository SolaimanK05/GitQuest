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
                "A branch is just a movable, named pointer to a commit, nothing heavier than that. Creating one "
                        + "is instant and cheap, because it doesn't copy any files, it only adds a new pointer. "
                        + "Switching branches (checkout) then moves your working directory to match whatever "
                        + "that pointer points to. This is what lets you work on something new without touching "
                        + "your main line of work at all.\n\n"
                        + "Create a new branch called feature and switch to it.",
                "Branches are how Git lets many people (or many ideas) develop in parallel without stepping on each other.",
                List.of(
                        TutorialStep.of("On main with one commit so far. A branch in Git is just a movable, "
                                + "named pointer to a commit. Nothing gets copied when you create one.",
                                "add .\ncommit -m \"Initial commit\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                                    executor.stageAll();
                                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("Watch a new pointer appear. Creating a branch is instant and cheap, "
                                + "since it only adds that pointer at the current commit. No files get "
                                + "duplicated anywhere on disk.",
                                "branch feature",
                                (model, executor) -> executor.createBranch("feature")),
                        TutorialStep.of("Switching moves your working directory to match whatever that pointer "
                                + "points to. Right now feature and main point at the exact same commit, so "
                                + "nothing on disk even changes yet.",
                                "checkout feature",
                                (model, executor) -> executor.checkout("feature")),
                        TutorialStep.of("From here, any new commits move only the feature pointer forward, "
                                + "leaving main exactly where it was. Your challenge repo is waiting on main "
                                + "with one commit. Create and switch to a feature branch yourself.")),
                List.of(
                        new ChecklistGoal("Create the feature branch", "Type: branch feature",
                                model -> model.snapshot().branches().stream().anyMatch(b -> b.name().equals("feature"))),
                        new ChecklistGoal("Switch to it", "Type: checkout feature",
                                new CurrentBranchGoal("feature")::isSatisfied)),
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
                        + "combine two separate histories at all. It can just slide that branch's pointer "
                        + "forward to match the tip of the branch you're merging in, like sliding a bookmark "
                        + "further down the same page. This is a fast-forward merge, and it's why merging is "
                        + "often instant: there's nothing to actually merge, only a pointer to move.\n\n"
                        + "You're on main. The feature branch has one commit main doesn't have yet. Merge "
                        + "feature into main.",
                "Fast-forward is the default and simplest case. Recognizing it is what makes the no-ff level right after this one make sense.",
                List.of(
                        TutorialStep.of("main sits at one commit. Let's give feature a head start of its own.",
                                "add .\ncommit -m \"Initial commit\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                                    executor.stageAll();
                                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("feature branches off and gets one commit of its own. main hasn't "
                                + "moved at all.",
                                "branch feature\ncheckout feature\nadd .\ncommit -m \"Add feature work\"\ncheckout main",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    executor.createBranch("feature");
                                    executor.checkout("feature");
                                    Files.writeString(workTree.resolve("feature.txt"), "New feature work.\n");
                                    executor.stageAll();
                                    executor.commit("Add feature work", AUTHOR_NAME, AUTHOR_EMAIL);
                                    executor.checkout("main");
                                }),
                        TutorialStep.of("Because main hasn't moved since feature branched off, merging doesn't "
                                + "need to combine two histories. Git can just slide main's pointer forward to "
                                + "match feature's tip. Watch: no new merge commit appears, main's pointer just "
                                + "jumps.",
                                "merge feature",
                                (model, executor) -> executor.merge("feature", false)),
                        TutorialStep.of("That's a fast-forward, the simplest and most common merge outcome. "
                                + "Your challenge repo is in the exact same shape: on main, with feature one "
                                + "commit ahead. Merge feature into main yourself.")),
                List.of(
                        new ChecklistGoal("Merge feature into main", "Type: merge feature",
                                new RefsConvergedGoal("main", "feature")::isSatisfied)),
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
                        + "on a separate branch. History just looks like a straight line, as if you'd been "
                        + "committing directly on main the whole time. Passing --no-ff forces Git to create a "
                        + "real merge commit (one with two parents) even when a fast-forward would have worked, "
                        + "preserving a visible record that a branch existed and was merged in. Many teams "
                        + "require this so their history clearly shows feature branches instead of a flattened "
                        + "line of commits.\n\n"
                        + "Same setup as before: main hasn't moved, feature has one commit ahead. This time, "
                        + "force a real merge commit.",
                "This is exactly the merge-commit shape you already saw in the Sandbox tutorial. Now you're making one on purpose.",
                List.of(
                        TutorialStep.of("Same shape as the fast-forward level a moment ago: main hasn't moved, "
                                + "feature is one commit ahead.",
                                "add .\ncommit -m \"Initial commit\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                                    executor.stageAll();
                                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("feature branches off and picks up one commit of its own.",
                                "branch feature\ncheckout feature\nadd .\ncommit -m \"Add feature work\"\ncheckout main",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    executor.createBranch("feature");
                                    executor.checkout("feature");
                                    Files.writeString(workTree.resolve("feature.txt"), "New feature work.\n");
                                    executor.stageAll();
                                    executor.commit("Add feature work", AUTHOR_NAME, AUTHOR_EMAIL);
                                    executor.checkout("main");
                                }),
                        TutorialStep.of("This time, pass --no-ff. Git is forced to create a real merge commit "
                                + "(one with two parents) even though a fast-forward would have worked. That "
                                + "merge commit is a permanent record that a branch existed and was merged in.",
                                "merge feature --no-ff",
                                (model, executor) -> executor.merge("feature", true)),
                        TutorialStep.of("Notice the shape on the graph: a real merge commit, not just a "
                                + "pointer jump. Many teams require this so history keeps showing feature "
                                + "branches instead of flattening into a single line. Do the same on your "
                                + "challenge repo. Force a real merge commit.")),
                List.of(
                        new ChecklistGoal("Merge feature into main with --no-ff", "Type: merge feature --no-ff",
                                new MergeCommitExistsGoal()::isSatisfied)),
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
                "Once a branch's work has been fully merged, keeping its name around just adds clutter, a label "
                        + "on a box you've already unpacked. Every commit it pointed to is already reachable "
                        + "from main, so deleting the branch loses nothing. Git also protects you here: deleting "
                        + "a branch refuses if it has commits main doesn't have yet, so you can't accidentally "
                        + "throw away unmerged work this way.\n\n"
                        + "The feature branch below is already fully merged into main. Delete it.",
                "Cleaning up merged branches is routine hygiene. Real repositories accumulate dozens of stale ones if nobody bothers.",
                List.of(
                        TutorialStep.of("feature's work is already fully merged into main. Every commit it "
                                + "pointed to is already reachable from main.",
                                "add .\ncommit -m \"Initial commit\"\nbranch feature\ncheckout feature\nadd .\n"
                                        + "commit -m \"Add feature work\"\ncheckout main\nmerge feature",
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
                                }),
                        TutorialStep.of("Once a branch's work is merged, keeping its name around is just "
                                + "clutter. Deleting it loses nothing, since the commits stay reachable through "
                                + "main.",
                                "delete feature",
                                (model, executor) -> executor.deleteBranch("feature")),
                        TutorialStep.of("feature is just gone from the graph, no error, because Git already "
                                + "confirmed nothing on it was unmerged. If it had unreachable commits, delete "
                                + "would have refused instead."),
                        TutorialStep.of("Your challenge repo has the same fully-merged feature branch waiting. "
                                + "Delete it yourself.")),
                List.of(
                        new ChecklistGoal("Delete the feature branch", "Type: delete feature",
                                new BranchAbsentGoal("feature")::isSatisfied)),
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
