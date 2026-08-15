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

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Controller for {@code EntryView.fxml}. {@code FXMLLoader} instantiates
 * this via a no-arg constructor and injects the {@code @FXML} fields before
 * calling {@link #initialize()} — {@link #setNavigator(Navigator)} is
 * called separately by {@code SceneRouter} right after load, since the
 * navigator isn't available at FXML-instantiation time.
 */
public final class EntryController {

    @FXML
    private TextField cloneUrlField;
    @FXML
    private Button cloneButton;
    @FXML
    private Button openButton;
    @FXML
    private Button initializeButton;

    private Navigator navigator;
    private final CommandService commandService = new CommandService();

    @FXML
    private void initialize() {
        cloneButton.setOnAction(e -> handleClone());
        openButton.setOnAction(e -> handleOpen());
        initializeButton.setOnAction(e -> handleInitialize());
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    private void handleClone() {
        String url = cloneUrlField.getText();
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
        cloneButton.setDisable(busy);
        openButton.setDisable(busy);
        initializeButton.setDisable(busy);
    }

    private Path chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        Window window = cloneUrlField.getScene() != null ? cloneUrlField.getScene().getWindow() : null;
        File selected = chooser.showDialog(window);
        return selected != null ? selected.toPath() : null;
    }
}
