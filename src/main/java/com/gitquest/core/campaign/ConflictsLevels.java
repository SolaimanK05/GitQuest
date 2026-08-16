package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Arc 3 content: induced conflicts, resolving, aborting a merge, per CLAUDE.md 4.2. */
final class ConflictsLevels {

    private static final String AUTHOR_NAME = "GitQuest Campaign";
    private static final String AUTHOR_EMAIL = "campaign@gitquest.local";

    private ConflictsLevels() {
    }

    static List<LevelDefinition> all() {
        return List.of(triggerAConflict(), resolveAConflict(), abortAMerge());
    }

    private static LevelDefinition triggerAConflict() {
        return new LevelDefinition(
                "conflicts-trigger-a-conflict",
                "conflicts",
                "Trigger a Conflict",
                "Git can only merge two histories automatically when it can tell how to combine their changes. "
                        + "When both branches edit the exact same lines of the exact same file in different "
                        + "ways, Git has no way to guess which version you want — it stops partway through the "
                        + "merge and asks you to decide. This is a conflict, and it's not an error or something "
                        + "broken: it's Git refusing to silently pick a winner on your behalf.\n\n"
                        + "main and feature have both edited the second line of notes.txt differently. Merge "
                        + "feature into main and watch it stop on a conflict instead of failing outright.",
                "Every Git user hits conflicts eventually — recognizing one calmly, instead of panicking, is half the battle.",
                "Type: merge feature",
                "Type \"merge feature\" and press Enter. The merge will report a conflict — that's the expected outcome for this level.",
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Path notes = workTree.resolve("notes.txt");
                    Files.writeString(notes, "Line one.\nOriginal line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(notes, "Line one.\nFeature's version of line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Feature edits line two", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                    Files.writeString(notes, "Line one.\nMain's version of line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Main edits line two", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new ConflictPendingGoal());
    }

    private static LevelDefinition resolveAConflict() {
        return new LevelDefinition(
                "conflicts-resolve-a-conflict",
                "conflicts",
                "Resolve a Conflict",
                "A conflicted file isn't corrupted — Git marks the disputed section right inside it, with "
                        + "<<<<<<<, =======, and >>>>>>> lines bracketing \"your\" version and \"their\" version "
                        + "side by side. Resolving means opening the file in your own editor, deciding what the "
                        + "final content should be, and deleting the marker lines entirely. Once the file looks "
                        + "the way you want, it's staged and committed exactly like any other change — that "
                        + "commit is what finally completes the merge.\n\n"
                        + "This repository is already stopped mid-conflict on notes.txt. Open it in your editor, "
                        + "resolve the conflict markers, then stage and commit to finish the merge.",
                "This exact edit-stage-commit sequence is how every merge conflict in the real world gets resolved, no matter the tool.",
                "Edit notes.txt to remove the <<<<<<<, =======, >>>>>>> markers and pick the final wording. "
                        + "Then type: add\nThen type: commit -m \"your message\"",
                "Remove the conflict markers from notes.txt, keeping whichever wording you like for line two, "
                        + "then type \"add\" and press Enter, then commit -m \"resolve conflict\".",
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Path notes = workTree.resolve("notes.txt");
                    Files.writeString(notes, "Line one.\nOriginal line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(notes, "Line one.\nFeature's version of line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Feature edits line two", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                    Files.writeString(notes, "Line one.\nMain's version of line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Main edits line two", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.merge("feature", false);
                },
                new ConflictsResolvedGoal());
    }

    private static LevelDefinition abortAMerge() {
        return new LevelDefinition(
                "conflicts-abort-a-merge",
                "conflicts",
                "Abort a Merge",
                "Sometimes a conflict shows up at a bad time, or you realize the merge shouldn't happen at all "
                        + "right now. Rather than resolving it, you can back out completely: --abort throws away "
                        + "everything the merge attempt touched and puts your branch back exactly how it was "
                        + "before you ran merge, as if you'd never tried. It only works while a merge is still "
                        + "unresolved — once you commit, that door closes.\n\n"
                        + "This repository is stopped mid-conflict on notes.txt again. This time, back out of it "
                        + "entirely instead of resolving it.",
                "Knowing you can always retreat safely makes it much less risky to just try a merge and see what happens.",
                "Type: merge --abort",
                "Type \"merge --abort\" and press Enter.",
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Path notes = workTree.resolve("notes.txt");
                    Files.writeString(notes, "Line one.\nOriginal line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Initial commit", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.createBranch("feature");
                    executor.checkout("feature");
                    Files.writeString(notes, "Line one.\nFeature's version of line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Feature edits line two", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.checkout("main");
                    Files.writeString(notes, "Line one.\nMain's version of line two.\nLine three.\n");
                    executor.stageAll();
                    executor.commit("Main edits line two", AUTHOR_NAME, AUTHOR_EMAIL);
                    executor.merge("feature", false);
                },
                new MergeAbortedGoal());
    }
}
