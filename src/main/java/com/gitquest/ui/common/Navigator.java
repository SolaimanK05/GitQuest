package com.gitquest.ui.common;

import com.gitquest.core.model.RepoStateModel;

/** Lets a screen controller move to the Sandbox screen without depending on the app's scene wiring. */
public interface Navigator {

    void showSandbox(RepoStateModel model);
}
