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
                        + "ways, Git has no way to guess which version you want, so it stops partway through the "
                        + "merge and asks you to decide. This is a conflict, and despite how alarming it looks "
                        + "the first time, it's not an error or something broken. It's Git refusing to quietly "
                        + "pick a winner behind your back.\n\n"
                        + "main and feature have both edited the second line of notes.txt differently. Merge "
                        + "feature into main and watch it stop on a conflict instead of failing outright.",
                "Every Git user hits conflicts eventually. Recognizing one calmly, instead of panicking, is half the battle.",
                List.of(
                        TutorialStep.of("main and feature have each edited the same line of notes.txt "
                                + "differently, which is a recipe for a conflict.",
                                "add .\ncommit -m \"Initial commit\"\nbranch feature\ncheckout feature\nadd .\n"
                                        + "commit -m \"Feature edits line two\"\ncheckout main\nadd .\n"
                                        + "commit -m \"Main edits line two\"",
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
                                }),
                        TutorialStep.of("Merging normally combines two histories automatically. But when both "
                                + "sides touch the very same lines differently, Git has no way to guess which "
                                + "version you want."),
                        TutorialStep.of("Watch what happens when we merge feature into main here.",
                                "merge feature",
                                (model, executor) -> executor.merge("feature", false)),
                        TutorialStep.of("See that pulsing highlight on the graph? That's a conflict, not a "
                                + "failure, just Git stopping partway through and asking you to decide. Your "
                                + "challenge repo is set up exactly the same way. Merge feature into main and "
                                + "trigger the same conflict yourself.")),
                List.of(
                        new ChecklistGoal("Merge feature into main", "Type: merge feature",
                                new ConflictPendingGoal()::isSatisfied)),
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
                "A conflicted file isn't corrupted, whatever it might look like at first glance. Git marks the "
                        + "disputed section right inside it, with <<<<<<<, =======, and >>>>>>> lines bracketing "
                        + "\"your\" version and \"their\" version side by side, like two answers left in the "
                        + "margin for you to pick between. Resolving means opening the file in your own editor, "
                        + "deciding what the final content should be, and deleting the marker lines entirely. "
                        + "Once the file looks the way you want, it's staged and committed exactly like any "
                        + "other change, and that commit is what finally completes the merge.\n\n"
                        + "This repository is already stopped mid-conflict on notes.txt. Open it in your editor, "
                        + "resolve the conflict markers, then stage and commit to finish the merge.",
                "This exact edit-stage-commit sequence is how every merge conflict in the real world gets resolved, no matter the tool.",
                List.of(
                        TutorialStep.of("This tutorial repo is already stopped mid-conflict on notes.txt, just "
                                + "like a real merge would leave it.",
                                "add .\ncommit -m \"Initial commit\"\nbranch feature\ncheckout feature\nadd .\n"
                                        + "commit -m \"Feature edits line two\"\ncheckout main\nadd .\n"
                                        + "commit -m \"Main edits line two\"\nmerge feature",
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
                                }),
                        TutorialStep.of("A conflicted file isn't corrupted. Git marks the disputed section "
                                + "right inside it with <<<<<<< / ======= / >>>>>>> markers, bracketing \"yours\" "
                                + "and \"theirs\" side by side."),
                        TutorialStep.of("Resolving means deciding the final content and removing those markers. "
                                + "Here, we'll keep both edits, one after the other, then stage and commit "
                                + "exactly like any other change.",
                                "add .\ncommit -m \"Merge feature into main\"",
                                (model, executor) -> {
                                    Path notes = model.getRepository().getWorkTree().toPath().resolve("notes.txt");
                                    Files.writeString(notes,
                                            "Line one.\nMain's version of line two.\nFeature's version of line two.\nLine three.\n");
                                    executor.stageAll();
                                    executor.commit("Merge feature into main", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("That commit is what finishes the merge. Notice it has two parents, "
                                + "just like the no-ff merge from Arc 2. Your challenge repo is stopped on the "
                                + "same conflict. Resolve notes.txt in your own editor, then stage and commit "
                                + "to finish it yourself.")),
                List.of(
                        new ChecklistGoal("Resolve notes.txt and stage it",
                                "Edit notes.txt in your own editor to remove the <<<<<<< / ======= / >>>>>>> "
                                        + "markers, decide the final content, save it, then type: add",
                                model -> new com.gitquest.core.command.CommandExecutor(model).status().conflicting().isEmpty()),
                        new ChecklistGoal("Commit to finish the merge", "Type: commit -m \"your message\"",
                                new ConflictsResolvedGoal()::isSatisfied)),
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
                "Sometimes a conflict shows up at a bad time, or you realize partway through that the merge "
                        + "shouldn't happen at all right now. Rather than resolving it, you can back out "
                        + "completely: --abort throws away everything the merge attempt touched and puts your "
                        + "branch back exactly how it was before you ran merge, as if you'd never tried. It only "
                        + "works while a merge is still unresolved. Once you commit, that door closes for good.\n\n"
                        + "This repository is stopped mid-conflict on notes.txt again. This time, back out of it "
                        + "entirely instead of resolving it.",
                "Knowing you can always retreat safely makes it much less risky to just try a merge and see what happens.",
                List.of(
                        TutorialStep.of("Stopped mid-conflict on notes.txt again, same setup as the resolve "
                                + "level a moment ago.",
                                "add .\ncommit -m \"Initial commit\"\nbranch feature\ncheckout feature\nadd .\n"
                                        + "commit -m \"Feature edits line two\"\ncheckout main\nadd .\n"
                                        + "commit -m \"Main edits line two\"\nmerge feature",
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
                                }),
                        TutorialStep.of("This time, instead of resolving it, let's just back out entirely. "
                                + "--abort throws away everything the merge attempt touched and puts your "
                                + "branch back exactly how it was before you ran merge."),
                        TutorialStep.of("As if you'd never tried.",
                                "merge --abort",
                                (model, executor) -> executor.abortMerge()),
                        TutorialStep.of("notes.txt is back to Main's version, no conflict markers, no merge "
                                + "commit, just a clean single-parent HEAD, same as before the merge started. This "
                                + "only works while a merge is still unresolved; once you commit, that door "
                                + "closes. Your challenge repo has the same conflict waiting. Abort it "
                                + "yourself.")),
                List.of(
                        new ChecklistGoal("Abort the merge", "Type: merge --abort",
                                new MergeAbortedGoal()::isSatisfied)),
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
