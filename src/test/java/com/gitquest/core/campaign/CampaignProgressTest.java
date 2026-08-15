package com.gitquest.core.campaign;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CampaignProgressTest {

    @Test
    void newProgressHasNoCompletions() {
        CampaignProgress progress = new CampaignProgress();
        assertFalse(progress.isLevelCompleted("foundations-first-commit"));
        assertEquals(0, progress.completedCount());
    }

    @Test
    void recordCompletionIsIdempotent() {
        CampaignProgress progress = new CampaignProgress();
        progress.recordCompletion("foundations-first-commit", 1);
        // A replay attempt must never overwrite the original completion record.
        progress.recordCompletion("foundations-first-commit", 2);

        assertEquals(1, progress.completedCount());
        assertEquals(1, progress.completions().get("foundations-first-commit").hintTierUsed());
    }

    @Test
    void firstArcAlwaysUnlocked() {
        CampaignProgress progress = new CampaignProgress();
        assertTrue(progress.isArcUnlocked("foundations"));
    }

    @Test
    void secondArcUnlocksOnceFirstArcComplete() {
        CampaignProgress progress = new CampaignProgress();
        assertFalse(progress.isArcUnlocked("branching"));

        for (LevelDefinition level : CampaignCatalog.levelsForArc("foundations")) {
            progress.recordCompletion(level.id(), 0);
        }

        assertTrue(progress.isArcUnlocked("branching"));
    }

    @Test
    void arcAfterUnauthoredArcStaysLockedRegardlessOfProgress() {
        CampaignProgress progress = new CampaignProgress();
        for (LevelDefinition level : CampaignCatalog.levelsForArc("foundations")) {
            progress.recordCompletion(level.id(), 0);
        }

        // "branching" (arc 2) has no authored levels yet, so its empty level
        // list must not vacuously "complete" it and unlock "conflicts" (arc 3).
        assertFalse(progress.isArcUnlocked("conflicts"));
    }

    @Test
    void firstLevelInCampaignIsAlwaysUnlocked() {
        CampaignProgress progress = new CampaignProgress();
        LevelDefinition first = CampaignCatalog.allLevelsInOrder().get(0);
        assertTrue(progress.isLevelUnlocked(first.id()));
    }

    @Test
    void laterLevelsAreLockedUntilThePreviousOneIsCompleted() {
        CampaignProgress progress = new CampaignProgress();
        List<LevelDefinition> all = CampaignCatalog.allLevelsInOrder();
        LevelDefinition second = all.get(1);
        LevelDefinition third = all.get(2);

        assertFalse(progress.isLevelUnlocked(second.id()));
        assertFalse(progress.isLevelUnlocked(third.id()));

        progress.recordCompletion(all.get(0).id(), 0);
        assertTrue(progress.isLevelUnlocked(second.id()));
        assertFalse(progress.isLevelUnlocked(third.id()));

        progress.recordCompletion(second.id(), 0);
        assertTrue(progress.isLevelUnlocked(third.id()));
    }
}
