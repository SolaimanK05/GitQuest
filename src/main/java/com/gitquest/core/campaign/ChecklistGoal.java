package com.gitquest.core.campaign;

import java.util.function.Predicate;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.validation.GoalSpec;

/**
 * One item in a level's objective checklist (CLAUDE.md 4.2 follow-up: a concrete "here's what's
 * left" breakdown alongside the free-text objective) — a short label, its own hint (shown inline,
 * collapsed, under that specific item — not one hint covering the whole level), and an arbitrary
 * predicate over repo state. Not the level's actual completion condition (see
 * {@link LevelDefinition#goal()}); purely a visual progress breakdown re-checked the same way the
 * real goal is, after every command.
 */
public record ChecklistGoal(String label, String hint, Predicate<RepoStateModel> check) implements GoalSpec {

    @Override
    public boolean isSatisfied(RepoStateModel model) {
        return check.test(model);
    }

    @Override
    public String describeObjective() {
        return label;
    }
}
