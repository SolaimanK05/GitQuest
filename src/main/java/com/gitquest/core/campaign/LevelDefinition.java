package com.gitquest.core.campaign;

import com.gitquest.core.validation.GoalSpec;

/**
 * One Campaign level: starting state → objective → tiered hints → real-world
 * context, validated against end state via {@link #goal()}. See CLAUDE.md 4.2.
 */
public record LevelDefinition(
        String id,
        String arcId,
        String title,
        String objective,
        String whyItMatters,
        String freeHint,
        String solutionHint,
        LevelSetup setup,
        GoalSpec goal) {
}
