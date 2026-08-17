package com.gitquest.core.campaign;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which levels are completed, which levels' tutorials have already been watched, and derived
 * arc-unlock state. Per CLAUDE.md's campaign-save requirement: a replay of an already-completed
 * level must never alter its stored record — {@link #recordCompletion} only ever writes a level's
 * *first* completion ({@code putIfAbsent}), so calling it again (a replay, win or lose) is always
 * a no-op for that level.
 */
public final class CampaignProgress {

    private final Map<String, LevelCompletionRecord> completions;
    private final Set<String> tutorialsWatched;

    public CampaignProgress() {
        this(Map.of(), Set.of());
    }

    public CampaignProgress(Map<String, LevelCompletionRecord> completions) {
        this(completions, Set.of());
    }

    public CampaignProgress(Map<String, LevelCompletionRecord> completions, Set<String> tutorialsWatched) {
        this.completions = new LinkedHashMap<>(completions);
        this.tutorialsWatched = new LinkedHashSet<>(tutorialsWatched);
    }

    public boolean isLevelCompleted(String levelId) {
        return completions.containsKey(levelId);
    }

    /**
     * Strict linear progression through the whole campaign — no skipping.
     * The first level is always unlocked; every other level unlocks only
     * once the level immediately before it in campaign-wide order
     * ({@link CampaignCatalog#allLevelsInOrder()}) is completed.
     */
    public boolean isLevelUnlocked(String levelId) {
        List<LevelDefinition> all = CampaignCatalog.allLevelsInOrder();
        int index = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id().equals(levelId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return false;
        }
        if (index == 0) {
            return true;
        }
        return isLevelCompleted(all.get(index - 1).id());
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

    /** Once watched (finished or explicitly skipped), a level's tutorial no longer auto-shows on Play — see {@code CampaignController}. */
    public boolean hasTutorialBeenWatched(String levelId) {
        return tutorialsWatched.contains(levelId);
    }

    public void markTutorialWatched(String levelId) {
        tutorialsWatched.add(levelId);
    }

    public Set<String> tutorialsWatched() {
        return Set.copyOf(tutorialsWatched);
    }
}
