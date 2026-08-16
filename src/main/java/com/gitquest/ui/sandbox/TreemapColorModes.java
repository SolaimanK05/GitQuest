package com.gitquest.ui.sandbox;

import java.util.Map;
import java.util.function.Function;

import com.gitquest.core.codebase.CodebaseAnalyzer.FileStats;
import com.gitquest.core.codebase.FileEntry;

import javafx.scene.paint.Color;

/**
 * Color functions for {@link TreemapView}'s "Color by" overlays: a plain
 * categorical mode for the default size-only view, plus the churn heatmap
 * and recency fade called out in CLAUDE.md 4.3. Files with no commit history
 * yet (new/untracked) render as a distinct amber in both heatmap modes,
 * since "not committed at all" is itself meaningful information, not just
 * the cold/stale end of the scale.
 */
final class TreemapColorModes {

    private static final Color UNTRACKED = Color.web("#FFC857");
    private static final Color CHURN_COLD = Color.web("#1E3A5F");
    private static final Color CHURN_HOT = Color.web("#F05133");
    private static final Color RECENCY_STALE = Color.web("#3A3A3C");
    private static final Color RECENCY_FRESH = Color.web("#3DDC97");

    private TreemapColorModes() {
    }

    static Function<FileEntry, Color> byExtension() {
        return entry -> LanePalette.forLane(extensionOf(entry.name()).hashCode());
    }

    static Function<FileEntry, Color> byChurn(Map<String, FileStats> stats) {
        int maxCommits = stats.values().stream().mapToInt(FileStats::commitCount).max().orElse(1);
        return entry -> {
            FileStats fileStats = stats.get(entry.relativePath());
            if (fileStats == null || fileStats.commitCount() == 0) {
                return UNTRACKED;
            }
            double t = maxCommits > 0 ? clamp01((double) fileStats.commitCount() / maxCommits) : 0;
            return CHURN_COLD.interpolate(CHURN_HOT, t);
        };
    }

    static Function<FileEntry, Color> byRecency(Map<String, FileStats> stats) {
        long newest = stats.values().stream().mapToLong(FileStats::lastCommitEpochSeconds).max().orElse(0);
        long oldest = stats.values().stream()
                .filter(s -> s.commitCount() > 0)
                .mapToLong(FileStats::lastCommitEpochSeconds).min().orElse(newest);
        long span = Math.max(newest - oldest, 1);
        return entry -> {
            FileStats fileStats = stats.get(entry.relativePath());
            if (fileStats == null || fileStats.commitCount() == 0) {
                return UNTRACKED;
            }
            double t = clamp01((double) (fileStats.lastCommitEpochSeconds() - oldest) / span);
            return RECENCY_STALE.interpolate(RECENCY_FRESH, t);
        };
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toLowerCase() : "";
    }
}
