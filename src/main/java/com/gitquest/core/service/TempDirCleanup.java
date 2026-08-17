package com.gitquest.core.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.util.FileUtils;

/**
 * Recursively deletes disposable sandbox temp directories (CLAUDE.md's true-sandbox guarantee).
 *
 * <p>Uses JGit's own {@link FileUtils#delete} with {@code RETRY} rather than a plain
 * {@code Files.walk} delete: on Windows, a just-cloned repo's pack file can still be held open by
 * JGit's global window cache for a short while after the owning {@code Repository} is closed, and
 * Windows (unlike POSIX) refuses to delete a file that's still open anywhere in the process — a
 * plain delete attempt fails outright, while JGit's own retry/backoff logic (built for exactly
 * this problem, since JGit's own test suite hits it constantly) reliably gets there.
 */
public final class TempDirCleanup {

    private TempDirCleanup() {
    }

    /** Best-effort: a directory that's already gone, or a stray locked file, doesn't stop the rest from being cleaned up. */
    public static void deleteAll(List<Path> roots) {
        for (Path root : roots) {
            deleteRecursively(root);
        }
    }

    public static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            FileUtils.delete(root.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY | FileUtils.SKIP_MISSING);
        } catch (IOException ignored) {
            // best-effort cleanup; a genuinely locked file just gets left behind
        }
    }
}
