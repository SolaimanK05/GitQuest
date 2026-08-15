package com.gitquest.app;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.ui.common.Navigator;
import com.gitquest.ui.entry.EntryController;
import com.gitquest.ui.sandbox.SandboxController;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Minimal two-screen navigation: loads {@code EntryView.fxml}/
 * {@code SandboxView.fxml} and swaps the root of one {@link Scene}/
 * {@link Stage}. Each screen's controller is instantiated by
 * {@link FXMLLoader} itself (no-arg constructor + {@code @FXML} field
 * injection); this class finishes wiring it via {@code setNavigator}/
 * {@code setModel} right after load, before the scene is shown.
 */
public final class SceneRouter implements Navigator {

    private final Stage stage;
    private Scene scene;

    public SceneRouter(Stage stage) {
        this.stage = stage;
    }

    public void showEntry() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/EntryView.fxml"));
        Parent root = load(loader);
        EntryController controller = loader.getController();
        controller.setNavigator(this);
        setRoot(root);
    }

    @Override
    public void showSandbox(RepoStateModel model) {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SandboxView.fxml"));
        Parent root = load(loader);
        SandboxController controller = loader.getController();
        controller.setModel(model);
        setRoot(root);
    }

    private Parent load(FXMLLoader loader) {
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + loader.getLocation(), e);
        }
    }

    private void setRoot(Parent root) {
        if (scene == null) {
            scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/css/dark-theme.css").toExternalForm());
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
    }
}
