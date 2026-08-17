package com.gitquest.core.service;

import java.nio.file.Files;
import java.nio.file.Path;

/** Per CLAUDE.md 4.1: an "Open" folder is only valid if it actually contains a {@code .git} directory. */
public final class GitDirectoryValidator {

    private GitDirectoryValidator() {
    }

    public static boolean isGitRepository(Path dir) {
        return Files.isDirectory(dir.resolve(".git"));
    }
}
