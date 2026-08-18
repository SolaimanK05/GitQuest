package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.api.ResetCommand.ResetType;

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
                "Git almost never actually deletes anything right away. Every place HEAD has ever pointed "
                        + "is recorded in the reflog, a local safety net separate from your commit history. "
                        + "reset --hard <commit> moves your branch straight to that commit and makes the "
                        + "working tree match it exactly, discarding anything after it. It's the most "
                        + "destructive of the reset modes, fine for a commit only you have, since the reflog "
                        + "keeps it recoverable for a while if you change your mind.\n\n"
                        + "The last commit on main broke notes.txt. Use reflog to see where HEAD was before "
                        + "it, then hard-reset back to that point to throw the bad commit away entirely.",
                "Knowing reset --hard exists, and that it's reflog-recoverable rather than truly gone, is what makes it safe to experiment boldly.",
                List.of(
                        TutorialStep.of("One good commit, then a bad one that breaks notes.txt, the kind of "
                                + "thing that happens to everyone eventually.",
                                "add .\ncommit -m \"Good commit\"\nadd .\ncommit -m \"Bad commit\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Path notes = workTree.resolve("notes.txt");
                                    Files.writeString(notes, "Line one.\n");
                                    executor.stageAll();
                                    executor.commit("Good commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                    Files.writeString(notes, "Line one.\nBROKEN\n");
                                    executor.stageAll();
                                    executor.commit("Bad commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("Git almost never deletes anything right away. Every place HEAD has "
                                + "pointed is recorded in the reflog, a local safety net separate from your "
                                + "commit history. Right now it would show both commits, newest first, each "
                                + "with the message that created it."),
                        TutorialStep.of("reset --hard <commit> moves your branch straight to that commit, "
                                + "discarding anything after it.",
                                "reset --hard HEAD~1",
                                (model, executor) -> executor.reset("HEAD~1", ResetType.HARD)),
                        TutorialStep.of("The bad commit is gone from the graph, but it isn't truly gone yet. "
                                + "reflog keeps it recoverable for a while if you change your mind. Your "
                                + "challenge repo has the same bad commit. Use reflog to see where HEAD was, "
                                + "then hard-reset back yourself.")),
                List.of(
                        new ChecklistGoal("Hard-reset back to the good commit",
                                "Type: reflog to find the commit before the bad one, then: reset --hard HEAD~1",
                                new HeadMessageAndCountGoal("Good commit", 1)::isSatisfied)),
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
                "reset --soft <commit> only moves the branch pointer. The index and working tree are "
                        + "left completely untouched. Anything that was committed after <commit> comes back as "
                        + "already-staged changes, ready to be committed again differently. It's a gentler "
                        + "alternative to amend when what you actually want is to uncommit and start the "
                        + "commit over, rather than just editing the last one in place.\n\n"
                        + "The last commit on main is labeled just \"wip\", not a real message. Soft-reset it "
                        + "away, then commit the same (still-staged) change again with the message "
                        + "\"Add login validation\".",
                "Nothing about the file changes here. Only the commit boundary and its message do, which is exactly what --soft is for.",
                List.of(
                        TutorialStep.of("One real commit, then a sloppy \"wip\" commit thrown on top.",
                                "add .\ncommit -m \"Initial commit\"\nadd .\ncommit -m \"wip\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Files.writeString(workTree.resolve("login.txt"), "validate credentials\n");
                                    executor.stageAll();
                                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                    Files.writeString(workTree.resolve("login.txt"), "validate credentials\ncheck expiry\n");
                                    executor.stageAll();
                                    executor.commit("wip", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("reset --soft <commit> only moves the branch pointer. The index and "
                                + "working tree are left completely untouched.",
                                "reset --soft HEAD~1",
                                (model, executor) -> executor.reset("HEAD~1", ResetType.SOFT)),
                        TutorialStep.of("Notice: the file changes from that wip commit are still right there, "
                                + "already staged. Nothing about the actual content changed, only the commit "
                                + "boundary did. Now commit it again, properly.",
                                "commit -m \"Add login validation\"",
                                (model, executor) -> executor.commit("Add login validation", AUTHOR_NAME, AUTHOR_EMAIL)),
                        TutorialStep.of("One clean commit with a real message, same content as before. Your "
                                + "challenge repo has the same sloppy wip commit waiting. Soft-reset it away "
                                + "and recommit it yourself.")),
                List.of(
                        new ChecklistGoal("Soft-reset off the wip commit", "Type: reset --soft HEAD~1",
                                model -> {
                                    var status = new com.gitquest.core.command.CommandExecutor(model).status();
                                    return status.added().contains("login.txt") || status.changed().contains("login.txt")
                                            || new HeadMessageAndCountGoal("Add login validation", 2).isSatisfied(model);
                                }),
                        new ChecklistGoal("Commit again with a real message",
                                "Type: commit -m \"Add login validation\"",
                                new HeadMessageAndCountGoal("Add login validation", 2)::isSatisfied)),
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
                "reset and amend both rewrite history, which is fine when only you have seen those commits "
                        + "but dangerous the moment anyone else has pulled them. revert takes the opposite "
                        + "approach: it adds a brand new commit that undoes an earlier one's changes, leaving "
                        + "the original commit right where it was. History only ever grows, never gets "
                        + "rewritten, so it's safe to use on anything already shared.\n\n"
                        + "Imagine the last commit on main was already pushed and teammates have pulled it, "
                        + "but it broke notes.txt. Revert it instead of resetting, to fix the file without "
                        + "erasing that commit from history.",
                "This is the difference that matters most in Arc 6: reset/amend for your own unshared work, revert for anything already public.",
                List.of(
                        TutorialStep.of("Same shape as the hard-reset level: a good commit, then a bad one "
                                + "that breaks notes.txt. But this time imagine it's already pushed and "
                                + "teammates have pulled it.",
                                "add .\ncommit -m \"Good commit\"\nadd .\ncommit -m \"Bad commit\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Path notes = workTree.resolve("notes.txt");
                                    Files.writeString(notes, "Line one.\n");
                                    executor.stageAll();
                                    executor.commit("Good commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                    Files.writeString(notes, "Line one.\nBROKEN\n");
                                    executor.stageAll();
                                    executor.commit("Bad commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("reset and amend both rewrite history, which is dangerous once "
                                + "something's shared. revert takes the opposite approach: it adds a brand-new "
                                + "commit that undoes an earlier one's changes.",
                                "revert HEAD",
                                (model, executor) -> executor.revert("HEAD")),
                        TutorialStep.of("Look at the graph: the bad commit is still there, untouched, but a "
                                + "new commit right after it brings notes.txt back to how it looked before. "
                                + "History only ever grows here, never gets rewritten."),
                        TutorialStep.of("Safe to use on anything already shared, unlike reset or amend. Your "
                                + "challenge repo has the same bad commit, already \"pushed\" in spirit. "
                                + "Revert it yourself instead of resetting.")),
                List.of(
                        new ChecklistGoal("Revert the bad commit", "Type: revert HEAD",
                                new RevertRestoresContentGoal("notes.txt", "Line one.\n", 3)::isSatisfied)),
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
        AtomicReference<String> lostCommitId = new AtomicReference<>();
        return new LevelDefinition(
                "recovery-recover-a-deleted-branch",
                "recovery",
                "Recover a Deleted Branch",
                "Deleting a branch only removes the name pointing at its commits. The commits themselves "
                        + "stick around until Git eventually garbage-collects anything truly unreachable, which "
                        + "doesn't happen quickly or automatically. Until then, the reflog still remembers "
                        + "every commit HEAD ever visited, including ones on a branch you just deleted. "
                        + "Recovery is just: find the commit in the reflog, point a new branch at it.\n\n"
                        + "The prototype branch (with one commit of real work on it) has already been "
                        + "deleted. Use reflog to find that commit and recreate a branch pointing at it.",
                "This is the single most reassuring thing to know about Git: an accidental branch delete almost never actually loses anything.",
                List.of(
                        TutorialStep.of("A prototype branch, with one real commit on it, gets force-deleted "
                                + "here, simulating exactly the accident you're about to recover from.",
                                "branch prototype\ncheckout prototype\nadd .\ncommit -m \"Prototype experiment\"\ncheckout main",
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
                                    lostCommitId.set(model.getRepository().resolve("prototype").name());
                                    executor.checkout("main");
                                    model.getGit().branchDelete().setBranchNames("prototype").setForce(true).call();
                                    model.refresh();
                                }),
                        TutorialStep.of("Deleting a branch only removes the name. The commit itself sticks "
                                + "around until Git eventually garbage-collects it, which doesn't happen "
                                + "quickly. The reflog still remembers every commit HEAD ever visited, "
                                + "including this one."),
                        TutorialStep.of("Recovery is just: find the commit in the reflog, then point a brand "
                                + "new branch at it directly.",
                                "branch prototype <commit-id>",
                                (model, executor) -> executor.createBranchAt("prototype", lostCommitId.get())),
                        TutorialStep.of("prototype is back, pointing right at that same commit. Nothing was "
                                + "ever truly lost. Your challenge repo has the same deleted branch. Use "
                                + "reflog to find it and recreate it yourself.")),
                List.of(
                        new ChecklistGoal("Recreate the prototype branch at its lost commit",
                                "Type: reflog to find the commit message \"Prototype experiment\", then: branch prototype <that commit id>",
                                new BranchRecoveredGoal("Prototype experiment")::isSatisfied)),
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
