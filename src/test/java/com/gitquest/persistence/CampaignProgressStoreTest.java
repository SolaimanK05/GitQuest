package com.gitquest.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.gitquest.core.campaign.CampaignProgress;

class CampaignProgressStoreTest {

    @Test
    void roundTripsCompletionsThroughDisk() throws Exception {
        Path tempFile = Files.createTempFile("gitquest-progress-test", ".json");
        Files.delete(tempFile); // store must create it fresh
        CampaignProgressStore store = new CampaignProgressStore(tempFile);

        CampaignProgress progress = new CampaignProgress();
        progress.recordCompletion("foundations-first-commit", 1);
        progress.recordCompletion("foundations-build-a-timeline", 0);
        store.save(progress);

        CampaignProgress reloaded = store.load();

        assertTrue(reloaded.isLevelCompleted("foundations-first-commit"));
        assertTrue(reloaded.isLevelCompleted("foundations-build-a-timeline"));
        assertFalse(reloaded.isLevelCompleted("foundations-gitignore-habits"));
        assertEquals(1, reloaded.completions().get("foundations-first-commit").hintTierUsed());
    }

    @Test
    void loadingMissingFileReturnsEmptyProgress() {
        Path missing = Path.of(System.getProperty("java.io.tmpdir"),
                "gitquest-does-not-exist-" + System.nanoTime() + ".json");
        CampaignProgressStore store = new CampaignProgressStore(missing);

        CampaignProgress progress = store.load();

        assertEquals(0, progress.completedCount());
    }
}
