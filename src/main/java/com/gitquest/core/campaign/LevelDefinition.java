package com.gitquest.core.campaign;

import java.util.List;

import com.gitquest.core.validation.GoalSpec;

/**
 * One Campaign level: a real, animated tutorial walkthrough (see {@link TutorialStep}) → starting
 * state → objective, with a checkbox breakdown (see {@link ChecklistGoal}) that each carry their
 * own inline hint — validated against end state via {@link #goal()}. See CLAUDE.md 4.2. An empty
 * {@code tutorial} list means this level has none yet — {@code CampaignController} skips straight
 * to the objective for those. An empty {@code checklist} means the objective is shown as plain
 * text only, no checkbox/hint breakdown yet.
 */
public record LevelDefinition(
        String id,
        String arcId,
        String title,
        String objective,
        String whyItMatters,
        List<TutorialStep> tutorial,
        List<ChecklistGoal> checklist,
        LevelSetup setup,
        GoalSpec goal) {
}
