package com.gitquest.core.campaign;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.RepoStateModel;

/**
 * One step in a level's pre-objective tutorial (CLAUDE.md 4.2 follow-up: a real walkthrough
 * before the graded challenge). {@code action} runs against a throwaway tutorial-only repo — never
 * the level's actual graded starting state — so the concept being taught is demonstrated with a
 * real, animated commit graph rather than a static illustration. {@code command} is the literal
 * command line that produced this step's change, shown verbatim above the narration — in the same
 * syntax GitQuest's own terminal input accepts (no "git " prefix) — so the walkthrough shows
 * exactly what's being typed, not just prose describing it.
 */
public record TutorialStep(String narration, String command, TutorialAction action) {

    @FunctionalInterface
    public interface TutorialAction {
        void run(RepoStateModel model, CommandExecutor executor) throws Exception;
    }

    private static final TutorialAction NONE = (model, executor) -> { };

    /** A step that's pure narration — nothing changes on the graph, so there's no command to show. */
    public static TutorialStep of(String narration) {
        return new TutorialStep(narration, null, NONE);
    }

    /**
     * A step whose action isn't a single command the player would ever type themselves — e.g.
     * background scene-setup simulating a teammate's independent push, or wiring up a local
     * "origin" mirror. No command is shown; showing one here would misleadingly suggest the
     * player should type it.
     */
    public static TutorialStep of(String narration, TutorialAction action) {
        return new TutorialStep(narration, null, action);
    }

    /** A step that runs a real command against the tutorial repo — {@code command} is displayed verbatim above the narration, in the exact syntax GitQuest's own terminal accepts. */
    public static TutorialStep of(String narration, String command, TutorialAction action) {
        return new TutorialStep(narration, command, action);
    }
}
