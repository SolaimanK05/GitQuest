package com.gitquest.core.campaign;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * Satisfied once {@code filePath} reads back {@code expectedContent} and the repo has at least
 * {@code minimumCommitCount} commits — a revert restores old content with a brand new commit
 * (history grows), unlike a reset which would erase the bad commit instead of covering for it.
 */
public record RevertRestoresContentGoal(String filePath, String expectedContent, int minimumCommitCount) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        if (model.snapshot().commits().size() < minimumCommitCount) {
            return false;
        }
        Path file = model.getRepository().getWorkTree().toPath().resolve(filePath);
        try {
            // JGit may write checked-out content back with CRLF line endings depending on platform/
            // core.autocrlf, even though the commit itself and expectedContent here use bare LF —
            // normalize both sides so that's not what this check is actually testing.
            return Files.isRegularFile(file) && normalizeLineEndings(Files.readString(file)).equals(normalizeLineEndings(expectedContent));
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n");
    }

    @Override
    public String describeObjective() {
        return "Revert the commit that broke " + filePath + ": restore its content with a new commit, not by rewriting history.";
    }
}
