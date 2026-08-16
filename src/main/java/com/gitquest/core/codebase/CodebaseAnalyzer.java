package com.gitquest.core.codebase;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Per-file churn/recency/contributor stats for the Codebase visualizer's
 * heatmap overlays and file detail panel (CLAUDE.md 4.3), computed via a
 * path-filtered {@code git log} per file, as the spec calls out directly.
 * Blocking (walks history); callers must run this off the JavaFX
 * Application Thread.
 */
public final class CodebaseAnalyzer {

    /** {@code commitCount == 0} for a file with no history yet (e.g. untracked on disk). */
    public record FileStats(
            String relativePath,
            int commitCount,
            long firstCommitEpochSeconds,
            long lastCommitEpochSeconds,
            String lastCommitMessage,
            Set<String> contributors) {

        static FileStats empty(String relativePath) {
            return new FileStats(relativePath, 0, 0, 0, "", Set.of());
        }
    }

    private CodebaseAnalyzer() {
    }

    public static FileStats analyze(Git git, String relativePath) throws GitAPIException {
        Iterable<RevCommit> history = git.log().addPath(relativePath).call();
        int commitCount = 0;
        long first = Long.MAX_VALUE;
        long last = Long.MIN_VALUE;
        String lastMessage = "";
        Set<String> contributors = new LinkedHashSet<>();

        for (RevCommit commit : history) {
            commitCount++;
            int time = commit.getCommitTime();
            if (time < first) {
                first = time;
            }
            if (time > last) {
                last = time;
                lastMessage = commit.getShortMessage();
            }
            if (commit.getAuthorIdent() != null) {
                contributors.add(commit.getAuthorIdent().getName());
            }
        }

        if (commitCount == 0) {
            return FileStats.empty(relativePath);
        }
        return new FileStats(relativePath, commitCount, first, last, lastMessage, contributors);
    }
}
