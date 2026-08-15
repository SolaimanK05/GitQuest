package com.gitquest.core.campaign;

import java.time.Instant;

/** {@code hintTierUsed}: 0 = no hint, 1 = free hint, 2 = solution revealed. */
public record LevelCompletionRecord(String levelId, Instant completedAt, int hintTierUsed) {
}
