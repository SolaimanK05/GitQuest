package com.gitquest.ui.collab;

import com.gitquest.core.service.CollaborationSessionFactory;
import com.gitquest.core.service.CollaborationSessionFactory.CollaborationPair;
import com.gitquest.core.service.CommandService;
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

    @FXML
    private void initialize() {
        backButton.setOnAction(e -> navigator.showHome());
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        cloneAController.setNavigator(navigator);
        cloneBController.setNavigator(navigator);

        statusLabel.setText("Setting up a shared origin and two clones...");
        commandService.submit(CollaborationSessionFactory::createPair,
                this::onPairReady,
                error -> {
                    statusLabel.setText("Couldn't set up the collaboration demo.");
                    ErrorDialogs.show("Couldn't start collaboration demo", error);
                });
    }

    private void onPairReady(CollaborationPair pair) {
        statusLabel.setText("Shared origin: " + pair.bareOrigin());
        cloneAController.setModel(pair.cloneA());
        cloneBController.setModel(pair.cloneB());
    }
}
