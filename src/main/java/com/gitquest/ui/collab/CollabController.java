package com.gitquest.ui.collab;

import com.gitquest.core.service.CollaborationSessionFactory;
import com.gitquest.core.service.CollaborationSessionFactory.CollaborationPair;
import com.gitquest.core.service.CommandService;
import com.gitquest.core.service.TempDirCleanup;
import com.gitquest.ui.common.ErrorDialogs;
import com.gitquest.ui.common.Navigator;
import com.gitquest.ui.sandbox.SandboxController;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controller for {@code CollabView.fxml} — the two-clone collaboration sandbox (CLAUDE.md 4.5):
 * two full, independent Sandbox sessions ({@code fx:include}-d twice, each getting its own
 * {@link SandboxController} instance for free) both tracking the same local bare "origin", side
 * by side. Nothing here re-implements git plumbing; it only builds the shared starting point
 * ({@link CollaborationSessionFactory}) and hands one clone to each embedded session.
 */
public final class CollabController {

    @FXML
    private Button backButton;
    @FXML
    private Label statusLabel;
    @FXML
    private SandboxController cloneAController;
    @FXML
    private SandboxController cloneBController;

    private final CommandService commandService = new CommandService();
    private Navigator navigator;
    private CollaborationPair pair;

    @FXML
    private void initialize() {
        backButton.setOnAction(e -> leaveCollaboration());
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        cloneAController.setNavigator(navigator);
        cloneBController.setNavigator(navigator);
        // Each embedded session's own Home button would only clean up its own clone, not its
        // sibling or their shared local origin -- this screen's one "Home" button owns that.
        cloneAController.hideHomeButton();
        cloneBController.hideHomeButton();

        statusLabel.setText("Setting up a shared origin and two clones...");
        commandService.submit(CollaborationSessionFactory::createPair,
                this::onPairReady,
                error -> {
                    statusLabel.setText("Couldn't set up the collaboration demo.");
                    ErrorDialogs.show("Couldn't start collaboration demo", error);
                });
    }

    private void onPairReady(CollaborationPair pair) {
        this.pair = pair;
        statusLabel.setText("Shared origin: " + pair.bareOrigin());
        cloneAController.setModel(pair.cloneA());
        cloneBController.setModel(pair.cloneB());
    }

    /** Both clones' own temp dirs are already tracked by their models; the shared bare origin is only known here. */
    private void leaveCollaboration() {
        if (pair == null) {
            navigator.showHome();
            return;
        }
        commandService.submit(() -> {
                    // Release JGit's file handles first -- Windows won't delete a file still open elsewhere.
                    pair.cloneA().close();
                    pair.cloneB().close();
                    TempDirCleanup.deleteAll(pair.cloneA().disposablePaths());
                    TempDirCleanup.deleteAll(pair.cloneB().disposablePaths());
                    TempDirCleanup.deleteRecursively(pair.bareOrigin());
                    return null;
                },
                ignored -> navigator.showHome(),
                error -> navigator.showHome()); // best-effort cleanup; still leave either way
    }
}
