package com.gitquest.ui.entry;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.lib.NullProgressMonitor;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.CommandService;
import com.gitquest.core.service.RepositorySessionFactory;
import com.gitquest.ui.common.ErrorDialogs;
import com.gitquest.ui.common.LoadingIndicator;
import com.gitquest.ui.common.Navigator;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

/**
 * Controller for {@code EntryView.fxml}. {@code FXMLLoader} instantiates
 * this via a no-arg constructor and injects the {@code @FXML} fields before
 * calling {@link #initialize()} — {@link #setNavigator(Navigator)} is
 * called separately by {@code SceneRouter} right after load, since the
 * navigator isn't available at FXML-instantiation time.
 *
 * <p>Every path here ends up in an app-managed temp sandbox directory (see
 * {@link RepositorySessionFactory}) — never the real folder a user picked, and never a real
 * network remote once the (one, unavoidable-for-Clone) initial network operation is done. "Open"
 * no longer needs to ask whether to initialize a non-git folder instead: since nothing here ever
 * writes back into the real folder either way, {@code openAsSandbox} just transparently does the
 * right thing for both cases.
 */
public final class EntryController {

    @FXML
    private Button backButton;
    @FXML
    private LoadingIndicator loadingIndicator;
    @FXML
    private Label loadingLabel;
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
        backButton.setOnAction(e -> navigator.showHome());
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
        setBusy(true, "Cloning repository...");
        commandService.submit(
                () -> {
                    Path destination = Files.createTempDirectory("gitquest-sandbox-clone-");
                    return RepositorySessionFactory.cloneAsSandbox(url, destination, NullProgressMonitor.INSTANCE);
                },
                this::onSessionReady,
                this::onSessionFailed);
    }

    private void handleOpen() {
        Path folder = chooseDirectory("Choose a folder to explore in the sandbox");
        if (folder == null) {
            return;
        }
        setBusy(true, "Opening folder...");
        commandService.submit(
                () -> {
                    Path sandboxDir = Files.createTempDirectory("gitquest-sandbox-open-");
                    return RepositorySessionFactory.openAsSandbox(folder, sandboxDir);
                },
                this::onSessionReady,
                this::onSessionFailed);
    }

    private void handleInitialize() {
        setBusy(true, "Initializing repository...");
        commandService.submit(
                () -> {
                    Path sandboxDir = Files.createTempDirectory("gitquest-sandbox-init-");
                    RepoStateModel model = RepositorySessionFactory.init(sandboxDir);
                    model.markDisposable(java.util.List.of(sandboxDir), true);
                    return model;
                },
                this::onSessionReady,
                this::onSessionFailed);
    }

    private void onSessionReady(RepoStateModel model) {
        setBusy(false, null);
        navigator.showSandbox(model);
    }

    private void onSessionFailed(Throwable error) {
        setBusy(false, null);
        ErrorDialogs.show("Couldn't start sandbox session", error);
    }

    private void setBusy(boolean busy, String message) {
        backButton.setDisable(busy);
        cloneButton.setDisable(busy);
        openButton.setDisable(busy);
        initializeButton.setDisable(busy);
        loadingIndicator.setActive(busy);
        loadingLabel.setText(message);
        loadingLabel.setVisible(busy);
        loadingLabel.setManaged(busy);
    }

    private Path chooseDirectory(String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        Window window = cloneUrlField.getScene() != null ? cloneUrlField.getScene().getWindow() : null;
        File selected = chooser.showDialog(window);
        return selected != null ? selected.toPath() : null;
    }
}
