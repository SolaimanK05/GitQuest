package com.gitquest.app;

/**
 * Plain (non-{@code Application}) entry point. Run this from Eclipse via
 * "Run As -> Java Application" — no VM args needed. Launching
 * {@link GitQuestApp} directly instead triggers the JavaFX launcher's
 * "must look modular" heuristic and fails with "JavaFX runtime components
 * are missing", since this project intentionally runs as a plain classpath
 * (non-JPMS) app. See CLAUDE.md project brief / phase-1 plan for why.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        GitQuestApp.main(args);
    }
}
