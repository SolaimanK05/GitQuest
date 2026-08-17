package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Arc 5 content: reflog, reset (soft/hard), revert vs. reset, recovering a deleted branch, per CLAUDE.md 4.2. */
final class RecoveryLevels {

    private static final String AUTHOR_NAME = "GitQuest Campaign";
    private static final String AUTHOR_EMAIL = "campaign@gitquest.local";

    private RecoveryLevels() {
    }

    static List<LevelDefinition> all() {
        return List.of(hardResetABadCommit(), softResetToRedo(), revertAPublicCommit(), recoverADeletedBranch());
    }

    private static LevelDefinition hardResetABadCommit() {
        return new LevelDefinition(
                "recovery-hard-reset-a-bad-commit",
                "recovery",
                "Undo a Bad Commit",
                "Git almost never actually deletes anything right away — every place HEAD has ever pointed "
                        + "is recorded in the reflog, a local safety net separate from your commit history. "
                        + "reset --hard <commit> moves your branch straight to that commit and makes the "
                        + "working tree match it exactly, discarding anything after it. It's the most "
                        + "destructive of the reset modes — fine for a commit only you have, since the reflog "
                        + "keeps it recoverable for a while if you change your mind.\n\n"
                        + "The last commit on main broke notes.txt. Use reflog to see where HEAD was before "
                        + "it, then hard-reset back to that point to throw the bad commit away entirely.",
                "Knowing reset --hard exists — and that it's reflog-recoverable, not truly gone — is what makes it safe to experiment boldly.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Path notes = workTree.resolve("notes.txt");
                    Files.writeString(notes, "Line one.\n");
                    executor.stageAll();
                    executor.commit("Good commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(notes, "Line one.\nBROKEN\n");
                    executor.stageAll();
                    executor.commit("Bad commit", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new HeadMessageAndCountGoal("Good commit", 1));
    }

    private static LevelDefinition softResetToRedo() {
        return new LevelDefinition(
                "recovery-soft-reset-to-redo",
                "recovery",
                "Soft Reset to Redo a Commit",
                "reset --soft <commit> only moves the branch pointer — the index and working tree are "
                        + "left completely untouched. Anything that was committed after <commit> comes back as "
                        + "already-staged changes, ready to be committed again differently. It's a gentler "
                        + "alternative to amend when what you actually want is to uncommit and start the "
                        + "commit over, rather than just editing the last one in place.\n\n"
                        + "The last commit on main is labeled just \"wip\" — not a real message. Soft-reset it "
                        + "away, then commit the same (still-staged) change again with the message "
                        + "\"Add login validation\".",
                "Nothing about the file changes here — only the commit boundary and its message do, which is exactly what --soft is for.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("login.txt"), "validate credentials\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(workTree.resolve("login.txt"), "validate credentials\ncheck expiry\n");
                    executor.stageAll();
                    executor.commit("wip", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new HeadMessageAndCountGoal("Add login validation", 2));
    }

    private static LevelDefinition revertAPublicCommit() {
        return new LevelDefinition(
                "recovery-revert-a-public-commit",
                "recovery",
                "Revert a Public Commit",
                "reset and amend both rewrite history — fine when only you have seen those commits, "
                        + "dangerous the moment anyone else has pulled them. revert takes the opposite "
                        + "approach: it adds a brand new commit that undoes an earlier one's changes, leaving "
                        + "the original commit right where it was. History only ever grows, never gets "
                        + "rewritten — safe to use on anything already shared.\n\n"
                        + "Imagine the last commit on main was already pushed and teammates have pulled it — "
                        + "but it broke notes.txt. Revert it instead of resetting, to fix the file without "
                        + "erasing that commit from history.",
                "This is the difference that matters most in Arc 6: reset/amend for your own unshared work, revert for anything already public.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Path notes = workTree.resolve("notes.txt");
                    Files.writeString(notes, "Line one.\n");
                    executor.stageAll();
                    executor.commit("Good commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(notes, "Line one.\nBROKEN\n");
                    executor.stageAll();
                    executor.commit("Bad commit", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new RevertRestoresContentGoal("notes.txt", "Line one.\n", 3));
    }

    private static LevelDefinition recoverADeletedBranch() {
        return new LevelDefinition(
                "recovery-recover-a-deleted-branch",
                "recovery",
                "Recover a Deleted Branch",
                "Deleting a branch only removes the name pointing at its commits — the commits themselves "
                        + "stick around until Git eventually garbage-collects anything truly unreachable, which "
                        + "doesn't happen quickly or automatically. Until then, the reflog still remembers "
                        + "every commit HEAD ever visited, including ones on a branch you just deleted. "
                        + "Recovery is just: find the commit in the reflog, point a new branch at it.\n\n"
                        + "The prototype branch (with one commit of real work on it) has already been "
                        + "deleted. Use reflog to find that commit and recreate a branch pointing at it.",
                "This is the single most reassuring thing to know about Git — an accidental branch delete almost never actually loses anything.",
                List.of(),
                List.of(),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("prototype");
                    executor.checkout("prototype");
                    Files.writeString(workTree.resolve("prototype.txt"), "an idea worth keeping\n");
                    executor.stageAll();
                    executor.commit("Prototype experiment", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                    model.getGit().branchDelete().setBranchNames("prototype").setForce(true).call();
                    model.refresh();
                },
                new BranchRecoveredGoal("Prototype experiment"));
    }
}
