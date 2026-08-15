package com.gitquest.core.campaign;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Static registry of all 6 arcs (per CLAUDE.md 4.2) and their levels. Only
 * Arc 1 (Foundations) has authored {@link LevelDefinition}s this pass —
 * arcs 2-6 carry metadata only and render as "coming soon" in the skill tree.
 */
public final class CampaignCatalog {

    public record ArcInfo(String id, String title, String description) {
    }

    private static final List<ArcInfo> ARCS = List.of(
            new ArcInfo("foundations", "Foundations", "init, add, commit, status, log, .gitignore"),
            new ArcInfo("branching", "Branching", "create/switch/delete branches, fast-forward vs. no-ff merge"),
            new ArcInfo("conflicts", "Conflicts", "induced conflicts, resolving, aborting a merge"),
            new ArcInfo("rewriting", "Rewriting History", "rebase, interactive rebase, cherry-pick, amend"),
            new ArcInfo("recovery", "Recovery", "reflog, reset, revert vs. reset, recovering a deleted branch"),
            new ArcInfo("remotes", "Remotes", "fetch vs. pull, push, tracking branches, force-push dangers"));

    private static final List<LevelDefinition> LEVELS = FoundationsLevels.all();

    private CampaignCatalog() {
    }

    public static List<ArcInfo> arcs() {
        return ARCS;
    }

    public static List<String> arcOrder() {
        return ARCS.stream().map(ArcInfo::id).collect(Collectors.toList());
    }

    public static List<LevelDefinition> levelsForArc(String arcId) {
        return LEVELS.stream().filter(level -> level.arcId().equals(arcId)).collect(Collectors.toList());
    }

    public static LevelDefinition levelById(String id) {
        return LEVELS.stream().filter(level -> level.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown level: " + id));
    }

    public static int totalLevelCount() {
        return LEVELS.size();
    }
}
