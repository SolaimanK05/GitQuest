package com.gitquest.ui.entry;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import org.eclipse.jgit.lib.NullProgressMonitor;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.CommandService;
import com.gitquest.core.service.RepositorySessionFactory;
import com.gitquest.ui.common.ErrorDialogs;
import com.gitquest.ui.common.Navigator;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/** Wires {@link EntryView}'s buttons to {@link RepositorySessionFactory}, off the FX thread via {@link CommandService}. */
public final class EntryController {

    private final EntryView view;
    private final Navigator navigator;
    private final CommandService commandService = new CommandService();

    public EntryController(EntryView view, Navigator navigator) {
        this.view = view;
        this.navigator = navigator;
        view.getCloneButton().setOnAction(e -> handleClone());
        view.getOpenButton().setOnAction(e -> handleOpen());
        view.getInitializeButton().setOnAction(e -> handleInitialize());
    }

    private void handleClone() {
        String url = view.getCloneUrlField().getText();
        if (url == null || url.isBlank()) {
            new Alert(AlertType.WARNING, "Enter a repository URL to clone.").showAndWait();
            return;
        }
        Path destination = chooseDirectory("Choose destination folder for clone");
        if (destination == null) {
            return;
        }
        setBusy(true);
        commandService.submit(
                () -> RepositorySessionFactory.clone(url, destination, NullProgressMonitor.INSTANCE),
                this::onSessionReady,
                this::onSessionFailed);
    }

    private void handleOpen() {
        Path folder = chooseDirectory("Choose a folder to open");
        if (folder == null) {
            return;
        }
        if (GitDirectoryValidator.isGitRepository(folder)) {
            setBusy(true);
            commandService.submit(
                    () -> RepositorySessionFactory.open(folder),
                    this::onSessionReady,
                    this::onSessionFailed);
            return;
        }
        Alert confirm = new Alert(AlertType.CONFIRMATION,
                folder + " is not a Git repository yet. Initialize it instead?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Not a Git repository");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.YES) {
            initialize(folder);
        }
    }

    private void handleInitialize() {
        Path folder = chooseDirectory("Choose a folder to initialize");
        if (folder == null) {
            return;
        }
        initialize(folder);
    }

    private void initialize(Path folder) {
        setBusy(true);
        commandService.submit(
                () -> RepositorySessionFactory.init(folder),
                this::onSessionReady,
                this::onSessionFailed);
    }

    private void onSessionReady(RepoStateModel model) {
        setBusy(false);
        navigator.showSandbox(model);
    }

    private void onSessionFailed(Throwable error) {
        setBusy(false);
        ErrorDialogs.show("Couldn't open repository", error);
    }

    private void setBusy(boolean busy) {
        view.getCloneButton().setDisable(busy);
        view.getOpenButton().setDisable(busy);
        view.getInitializeButton().setDisable(busy);
    }

    private Path chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        Window window = view.getScene() != null ? view.getScene().getWindow() : null;
        File selected = chooser.showDialog(window);
        return selected != null ? selected.toPath() : null;
    }
}
