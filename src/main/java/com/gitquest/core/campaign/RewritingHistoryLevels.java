package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Arc 4 content: amend, cherry-pick, rebase, interactive rebase (squash), per CLAUDE.md 4.2. */
final class RewritingHistoryLevels {

    private static final String AUTHOR_NAME = "GitQuest Campaign";
    private static final String AUTHOR_EMAIL = "campaign@gitquest.local";

    private RewritingHistoryLevels() {
    }

    static List<LevelDefinition> all() {
        return List.of(amendACommit(), cherryPickACommit(), rebaseYourBranch(), squashViaInteractiveRebase());
    }

    private static LevelDefinition amendACommit() {
        return new LevelDefinition(
                "rewriting-amend-a-commit",
                "rewriting",
                "Amend a Commit",
                "Committing is rarely perfect on the first try — a typo in the message, or a file you "
                        + "forgot to include. --amend replaces HEAD's commit entirely with a new one built "
                        + "from whatever's currently staged plus a message you give it, rather than adding "
                        + "another commit on top. It's a genuine rewrite: the old commit's SHA is gone, "
                        + "replaced by a brand new one — perfectly safe on work only you have, risky on "
                        + "anything already shared (more on that in Arc 6).\n\n"
                        + "notes.txt has a typo, and the last commit's message has one too "
                        + "(\"Fx bug in login\"). Fix the file, then amend the commit so the message reads "
                        + "exactly \"Fix bug in login\".",
                "This is the single most common history rewrite anyone does — cleaning up a commit before anyone else has seen it.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Path notes = workTree.resolve("notes.txt");
                    Files.writeString(notes, "Login flow notes.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(notes, "Login flow nots.\n"); // the typo to fix
                    executor.stageAll();
                    executor.commit("Fx bug in login", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new HeadMessageAndCountGoal("Fix bug in login", 2));
    }

    private static LevelDefinition cherryPickACommit() {
        return new LevelDefinition(
                "rewriting-cherry-pick-a-commit",
                "rewriting",
                "Cherry-Pick a Commit",
                "Sometimes you want exactly one commit from another branch — not everything on it. "
                        + "cherry-pick takes a single existing commit and replays just its changes onto your "
                        + "current branch as a brand new commit, leaving the rest of that branch's history "
                        + "behind entirely. It's the tool for \"I need that one fix, not the whole feature.\"\n\n"
                        + "You're on main. The feature branch has two commits: one adds urgent-fix.txt, the "
                        + "other adds experiment.txt (an unrelated, half-finished idea). Cherry-pick only the "
                        + "commit that adds urgent-fix.txt onto main.",
                "This is exactly how a hotfix buried in a feature branch gets pulled out and shipped without dragging the rest of that branch along.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(workTree.resolve("urgent-fix.txt"), "critical patch\n");
                    executor.stageAll();
                    executor.commit("Add urgent fix", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(workTree.resolve("experiment.txt"), "half-baked idea\n");
                    executor.stageAll();
                    executor.commit("Add experiment", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                },
                new SelectiveCherryPickGoal("urgent-fix.txt", "experiment.txt"));
    }

    private static LevelDefinition rebaseYourBranch() {
        return new LevelDefinition(
                "rewriting-rebase-your-branch",
                "rewriting",
                "Rebase Your Branch",
                "Merging isn't the only way to bring a branch up to date — rebase takes your branch's "
                        + "commits off, fast-forwards you to the tip of another branch, then replays your "
                        + "commits one by one on top of it. The result is a straight line of history, as if "
                        + "you'd started your work after that other branch's latest commit, instead of the "
                        + "fork-and-rejoin shape a merge leaves behind.\n\n"
                        + "You're on feature, which branched off main a while ago. main has since gained a "
                        + "new commit. Rebase feature onto main so your work sits cleanly on top of it.",
                "Many teams prefer a rebased, linear history on feature branches — it reads top to bottom like a single story instead of a web of merges.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("base.txt"), "shared base\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(workTree.resolve("feature.txt"), "feature work\n");
                    executor.stageAll();
                    executor.commit("Add feature work", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                    Files.writeString(workTree.resolve("base.txt"), "shared base\nmain moved on\n");
                    executor.stageAll();
                    executor.commit("Main moves on", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("feature");
                },
                new LinearRebaseOntoGoal("main"));
    }

    private static LevelDefinition squashViaInteractiveRebase() {
        return new LevelDefinition(
                "rewriting-squash-via-interactive-rebase",
                "rewriting",
                "Squash Commits (Interactive Rebase)",
                "Real work is often messy in the moment — several small \"wip\" commits while you figure "
                        + "something out. Interactive rebase lets you rewrite a whole stretch of history at "
                        + "once: reorder commits, drop them, reword them, or squash several into one. The "
                        + "squash use case alone is worth knowing well: turn a pile of in-progress commits "
                        + "into a single clean one before anyone else ever has to look at your history.\n\n"
                        + "You're on feature, which branched off main and picked up three messy \"wip\" "
                        + "commits. Squash all three into one commit — rebased onto main's tip — with the "
                        + "message \"Add search feature\".",
                "A reviewer (or future you, six months from now) would much rather see one commit titled \"Add search feature\" than three commits titled \"wip\".",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Path search = workTree.resolve("search.txt");
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(search, "step one\n");
                    executor.stageAll();
                    executor.commit("wip", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(search, "step one\nstep two\n");
                    executor.stageAll();
                    executor.commit("wip", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(search, "step one\nstep two\nstep three\n");
                    executor.stageAll();
                    executor.commit("wip", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new HeadMessageAndCountGoal("Add search feature", 2));
    }
}
