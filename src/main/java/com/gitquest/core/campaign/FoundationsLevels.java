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
                "A brand new Git repository starts completely empty — even right after git init, nothing is "
                        + "tracked yet. Git records history in two steps: staging marks which changes belong in "
                        + "the next snapshot, and committing actually saves that snapshot permanently, along "
                        + "with a message describing what changed. This two-step design is deliberate — it lets "
                        + "you build up exactly the change you want to record before you save it, even if your "
                        + "working directory has other unrelated edits in progress.\n\n"
                        + "This repository already has one file on disk, notes.txt, but Git isn't tracking it "
                        + "yet. Stage it, then commit it — that stage → commit cycle is the single most-repeated "
                        + "action in Git.",
                "It's the same cycle you'll use for every single change you ever make in Git.",
                "Type: add\nThen type: commit -m \"your message\"",
                "Type \"add\" and press Enter, then type commit -m \"first commit\" and press Enter.",
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
                "Every commit becomes a permanent point in your project's history that you can return to later "
                        + "— Git isn't a single save file that gets overwritten, it's a chain of snapshots, each "
                        + "one pointing back at the one before it. That chain is what \"git log\" shows you, and "
                        + "it's what makes recovery possible: if something breaks, you can always look back and "
                        + "see exactly what the project looked like at any earlier commit.\n\n"
                        + "This repository already has two commits recording earlier work. Change a file, then "
                        + "stage and commit again — watch a third point appear on the graph, continuing the same "
                        + "chain.",
                "Teams rely on this history constantly — code review, blame, and debugging all start with git log.",
                "Edit a file (e.g. append a line to notes.txt) with any text editor, then in the terminal: "
                        + "add, then commit -m \"your message\".",
                "Add a line to notes.txt on disk, then type add and press Enter, then commit -m \"third entry\".",
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
                        + "leave alone so they never get staged by accident — and because .gitignore is itself "
                        + "just a normal file, it needs to be committed too, so the rule applies for anyone else "
                        + "who clones the project, not just you.\n\n"
                        + "This repository has a build/ folder full of generated junk. Create a .gitignore file "
                        + "that excludes it, then stage and commit the .gitignore itself.",
                "Committing build output bloats history and causes noisy diffs for your whole team.",
                "Create a file named .gitignore containing the line: build/\n"
                        + "Then in the terminal: add, then commit -m \"your message\".",
                "Create .gitignore with the single line \"build/\", then type add and press Enter, then "
                        + "commit -m \"add gitignore\".",
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
