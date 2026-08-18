package com.gitquest.core.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Arc 1 content: init/add/commit/log/.gitignore, per CLAUDE.md 4.2. */
final class FoundationsLevels {

    private static final String AUTHOR_NAME = "GitQuest Campaign";
    private static final String AUTHOR_EMAIL = "campaign@gitquest.local";

    private FoundationsLevels() {
    }

    static List<LevelDefinition> all() {
        return List.of(firstCommit(), buildATimeline(), gitignoreHabits());
    }

    private static LevelDefinition firstCommit() {
        return new LevelDefinition(
                "foundations-first-commit",
                "foundations",
                "First Commit",
                "A brand-new Git repository starts out completely empty. Even right after init, nothing is "
                        + "tracked yet. Git records history in two deliberate steps: staging marks which changes "
                        + "belong in the next snapshot, and committing saves that snapshot permanently, with a "
                        + "message describing what changed. Splitting the two apart is deliberate, since it lets "
                        + "you build up exactly the change you want to record, even while other unrelated edits "
                        + "sit in your working directory.\n\n"
                        + "This repository already has one file on disk, notes.txt, but Git isn't tracking it "
                        + "yet. Stage it, then commit it. That stage-then-commit cycle is the single "
                        + "most-repeated action in Git.",
                "It's the same cycle you'll use for every single change you ever make in Git.",
                List.of(
                        TutorialStep.of("Here's a brand-new repository, completely empty. Even right after "
                                + "init, Git isn't tracking anything yet, so there's nothing on the graph at all."),
                        TutorialStep.of("Git records history in two deliberate steps: staging marks which "
                                + "changes belong in the next snapshot, and committing saves that snapshot for "
                                + "good. Splitting them apart means you can build up exactly the change you want "
                                + "before you save it."),
                        TutorialStep.of("Watch both steps happen back to back: stage hello.txt, then commit it. "
                                + "That single point landing on the graph is a permanent snapshot. It isn't "
                                + "going anywhere.",
                                "add .\ncommit -m \"My first commit\"",
                                (model, executor) -> {
                                    Files.writeString(model.getRepository().getWorkTree().toPath().resolve("hello.txt"),
                                            "Hello, Git.\n");
                                    executor.stageAll();
                                    executor.commit("My first commit", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("From here on, every change you ever make in Git follows this exact "
                                + "rhythm: edit a file, stage it, commit it. Your challenge repo has one "
                                + "untracked file waiting. Run through that same rhythm yourself.")),
                List.of(
                        new ChecklistGoal("Stage notes.txt", "Type: add", model -> {
                            var status = new com.gitquest.core.command.CommandExecutor(model).status();
                            return status.added().contains("notes.txt") || model.snapshot().commits().size() >= 1;
                        }),
                        new ChecklistGoal("Commit it", "Type: commit -m \"your message\"",
                                model -> model.snapshot().commits().size() >= 1)),
                (model, executor) -> Files.writeString(
                        model.getRepository().getWorkTree().toPath().resolve("notes.txt"),
                        "My first tracked file.\n"),
                new MinimumCommitCountGoal(1));
    }

    private static LevelDefinition buildATimeline() {
        return new LevelDefinition(
                "foundations-build-a-timeline",
                "foundations",
                "Build a Timeline",
                "Every commit becomes a permanent point in your project's history that you can return to later. "
                        + "Git isn't a single save file that gets overwritten, it's a chain of snapshots, each "
                        + "one pointing back at the one before it. That chain is what \"git log\" shows you, and "
                        + "it's what makes recovery possible: if something breaks, you can always look back and "
                        + "see exactly what the project looked like at any earlier commit.\n\n"
                        + "This repository already has two commits recording earlier work. Change a file, then "
                        + "stage and commit again, and watch a third point appear on the graph, continuing the "
                        + "same chain.",
                "Teams rely on this history constantly. Code review, blame, and debugging all start with git log.",
                List.of(
                        TutorialStep.of("A little history already on the books here: one commit recording some "
                                + "earlier work.",
                                "add .\ncommit -m \"First entry\"",
                                (model, executor) -> {
                                    Path notes = model.getRepository().getWorkTree().toPath().resolve("notes.txt");
                                    Files.writeString(notes, "Line one.\n");
                                    executor.stageAll();
                                    executor.commit("First entry", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("Git isn't a single save file that gets overwritten. It's a CHAIN. Each "
                                + "commit points back at the one right before it. Let's add a second link."),
                        TutorialStep.of("A second commit, attached below the first. Notice it didn't replace "
                                + "anything: both snapshots still exist, permanently, forever reachable.",
                                "add .\ncommit -m \"Second entry\"",
                                (model, executor) -> {
                                    Path notes = model.getRepository().getWorkTree().toPath().resolve("notes.txt");
                                    Files.writeString(notes, "Line one.\nLine two.\n");
                                    executor.stageAll();
                                    executor.commit("Second entry", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("One more, for good measure. This growing chain is exactly what "
                                + "\"git log\" walks through top to bottom, and it's what makes recovery "
                                + "possible later: you can always look back at any earlier point.",
                                "add .\ncommit -m \"Third entry\"",
                                (model, executor) -> {
                                    Path notes = model.getRepository().getWorkTree().toPath().resolve("notes.txt");
                                    Files.writeString(notes, "Line one.\nLine two.\nLine three.\n");
                                    executor.stageAll();
                                    executor.commit("Third entry", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("Your challenge repo already has two commits waiting, just like the "
                                + "start of this walkthrough. Add a third one yourself, the exact same way.")),
                List.of(
                        new ChecklistGoal("Edit and stage notes.txt",
                                "Open notes.txt in your own editor, add a line, save it, then type: add",
                                model -> {
                                    var status = new com.gitquest.core.command.CommandExecutor(model).status();
                                    return status.added().contains("notes.txt") || status.changed().contains("notes.txt")
                                            || model.snapshot().commits().size() >= 3;
                                }),
                        new ChecklistGoal("Commit a third time", "Type: commit -m \"your message\"",
                                model -> model.snapshot().commits().size() >= 3)),
                (model, executor) -> {
                    Path notes = model.getRepository().getWorkTree().toPath().resolve("notes.txt");
                    Files.writeString(notes, "Line one.\n");
                    executor.stageAll();
                    executor.commit("First entry", AUTHOR_NAME, AUTHOR_EMAIL);
                    Files.writeString(notes, "Line one.\nLine two.\n");
                    executor.stageAll();
                    executor.commit("Second entry", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new MinimumCommitCountGoal(3));
    }

    private static LevelDefinition gitignoreHabits() {
        return new LevelDefinition(
                "foundations-gitignore-habits",
                "foundations",
                ".gitignore Habits",
                "Not everything inside a project folder belongs in Git. Build output, compiled binaries, and "
                        + "other generated files change constantly, don't represent real project history, and "
                        + "just bloat your repository with noise. A .gitignore file tells Git which paths to "
                        + "leave alone so they never get staged by accident. And because .gitignore is itself "
                        + "just a normal file, it needs to be committed too, so the rule applies for anyone else "
                        + "who clones the project, not just you.\n\n"
                        + "This repository has a build/ folder full of generated junk. Create a .gitignore file "
                        + "that excludes it, then stage and commit the .gitignore itself.",
                "Committing build output bloats history and causes noisy diffs for your whole team.",
                List.of(
                        TutorialStep.of("A small project, already committed. But look closely: a build/ folder "
                                + "full of generated junk got swept in right along with the real source files.",
                                "add .\ncommit -m \"Initial project files (oops, build/ included)\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Files.createDirectories(workTree.resolve("build"));
                                    Files.writeString(workTree.resolve("build").resolve("output.class"), "junk\n");
                                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                                    executor.stageAll();
                                    executor.commit("Initial project files (oops, build/ included)", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("Build output changes constantly, doesn't represent real project "
                                + "history, and just adds noise. It doesn't belong in Git at all. A .gitignore "
                                + "file is how you tell Git to leave a path alone."),
                        TutorialStep.of("Committing the .gitignore itself matters, since it's just a normal file. "
                                + "Once it's committed, the rule applies for anyone who clones this repo, not "
                                + "only you.",
                                "add .\ncommit -m \"Ignore build output\"",
                                (model, executor) -> {
                                    Path workTree = model.getRepository().getWorkTree().toPath();
                                    Files.writeString(workTree.resolve(".gitignore"), "build/\n");
                                    executor.stageAll();
                                    executor.commit("Ignore build output", AUTHOR_NAME, AUTHOR_EMAIL);
                                }),
                        TutorialStep.of("From now on, build/ can sit right there on disk and Git will never "
                                + "offer to stage it again. Your challenge repo has the exact same junk waiting. "
                                + "Go set up its .gitignore yourself.")),
                List.of(
                        new ChecklistGoal("Create .gitignore excluding build/",
                                "Create a file named .gitignore in the project folder containing the line: build/",
                                model -> {
                                    Path gitignore = model.getRepository().getWorkTree().toPath().resolve(".gitignore");
                                    try {
                                        return java.nio.file.Files.isRegularFile(gitignore)
                                                && java.nio.file.Files.readString(gitignore).contains("build/");
                                    } catch (java.io.IOException e) {
                                        return false;
                                    }
                                }),
                        new ChecklistGoal("Stage and commit .gitignore",
                                "Type: add\nThen type: commit -m \"your message\"",
                                new GitignoreExcludesGoal("build/")::isSatisfied)),
                (model, executor) -> {
                    Path workTree = model.getRepository().getWorkTree().toPath();
                    Files.createDirectories(workTree.resolve("build"));
                    Files.writeString(workTree.resolve("build").resolve("output.class"), "junk\n");
                    Files.writeString(workTree.resolve("README.md"), "A sample project.\n");
                    executor.stageAll();
                    executor.commit("Initial project files", AUTHOR_NAME, AUTHOR_EMAIL);
                },
                new GitignoreExcludesGoal("build/"));
    }
}
