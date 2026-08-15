package com.gitquest.core.campaign;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which levels are completed, and derived arc-unlock state. Per CLAUDE.md's
 * campaign-save requirement: a replay of an already-completed level must
 * never alter its stored record — {@link #recordCompletion} only ever
 * writes a level's *first* completion ({@code putIfAbsent}), so calling it
 * again (a replay, win or lose) is always a no-op for that level.
 */
public final class CampaignProgress {

    private final Map<String, LevelCompletionRecord> completions;

    public CampaignProgress() {
        this(Map.of());
    }

    public CampaignProgress(Map<String, LevelCompletionRecord> completions) {
        this.completions = new LinkedHashMap<>(completions);
    }

    public boolean isLevelCompleted(String levelId) {
        return completions.containsKey(levelId);
    }

    /**
     * Arc 1 is always unlocked. Arc N+1 unlocks once every level of arc N is
     * completed — an arc with no authored levels yet can never count as
     * "completed", so it must not vacuously unlock the arc after it.
     */
    public boolean isArcUnlocked(String arcId) {
        List<String> order = CampaignCatalog.arcOrder();
        int index = order.indexOf(arcId);
        if (index <= 0) {
            return true;
        }
        String previousArcId = order.get(index - 1);
        List<LevelDefinition> previousLevels = CampaignCatalog.levelsForArc(previousArcId);
        if (previousLevels.isEmpty()) {
            return false;
        }
        return previousLevels.stream().allMatch(level -> isLevelCompleted(level.id()));
    }

    public void recordCompletion(String levelId, int hintTierUsed) {
        completions.putIfAbsent(levelId, new LevelCompletionRecord(levelId, Instant.now(), hintTierUsed));
    }

    public Map<String, LevelCompletionRecord> completions() {
        return Map.copyOf(completions);
    }

    public int completedCount() {
        return completions.size();
    }
}
