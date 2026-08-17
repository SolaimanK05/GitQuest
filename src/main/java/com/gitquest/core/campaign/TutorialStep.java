package com.gitquest.core.campaign;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.RepoStateModel;

/**
 * One step in a level's pre-objective tutorial (CLAUDE.md 4.2 follow-up: a real walkthrough
 * before the graded challenge). {@code action} runs against a throwaway tutorial-only repo — never
 * the level's actual graded starting state — so the concept being taught is demonstrated with a
 * real, animated commit graph rather than a static illustration.
 */
public record TutorialStep(String narration, TutorialAction action) {

    @FunctionalInterface
    public interface TutorialAction {
        void run(RepoStateModel model, CommandExecutor executor) throws Exception;
    }

    private static final TutorialAction NONE = (model, executor) -> { };

    /** A step that's pure narration — nothing changes on the graph. */
    public static TutorialStep of(String narration) {
        return new TutorialStep(narration, NONE);
    }

    public static TutorialStep of(String narration, TutorialAction action) {
        return new TutorialStep(narration, action);
    }
}
