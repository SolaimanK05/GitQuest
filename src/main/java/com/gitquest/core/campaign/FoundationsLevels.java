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
                "Stage and commit notes.txt.",
                "git init only creates an empty repository — nothing is tracked until you stage and commit. "
                        + "This is the smallest possible unit of Git history.",
                "Try the \"Stage All\" button, then \"Commit...\".",
                "Click Stage All, then Commit... and enter any commit message.",
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
                "This repo already has 2 commits. Add a third.",
                "Git history is a timeline of snapshots, not a single save file — being able to look back "
                        + "through it (git log) is what makes Git useful for recovery and collaboration.",
                "Edit any file, then Stage All and Commit again.",
                "Change notes.txt, Stage All, then Commit with a new message.",
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
                "This repo has a build/ folder full of junk output. Create a .gitignore that excludes it, "
                        + "then commit the .gitignore.",
                "Committing generated build output bloats history and causes noisy diffs for your whole "
                        + "team — .gitignore keeps it out from the start.",
                "Create a file named .gitignore containing the line: build/",
                "Create .gitignore with the single line \"build/\", then Stage All and Commit.",
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
