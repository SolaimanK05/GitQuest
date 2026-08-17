package com.gitquest.ui.common;

import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.core.model.RepoStateModel;

/** Lets a screen controller move between screens without depending on the app's scene wiring. */
public interface Navigator {

    void showHome();

    void showEntry();

    void showCampaign();

    void showSandbox(RepoStateModel model);

    void showSandboxForLevel(RepoStateModel model, LevelDefinition level);

    /** Two-clone collaboration sandbox (CLAUDE.md 4.5) — sets up its own pair of sessions, no model needed here. */
    void showCollaboration();
}
