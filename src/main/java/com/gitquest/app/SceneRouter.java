package com.gitquest.app;

import com.gitquest.core.model.RepoStateModel;
import com.gitquest.ui.common.Navigator;
import com.gitquest.ui.entry.EntryController;
import com.gitquest.ui.entry.EntryView;
import com.gitquest.ui.sandbox.SandboxController;
import com.gitquest.ui.sandbox.SandboxView;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** Minimal two-screen navigation: swaps the root of one {@link Scene}/{@link Stage}. */
public final class SceneRouter implements Navigator {

    private final Stage stage;
    private Scene scene;

    public SceneRouter(Stage stage) {
        this.stage = stage;
    }

    public void showEntry() {
        EntryView view = new EntryView();
        new EntryController(view, this);
        setRoot(view);
    }

    @Override
    public void showSandbox(RepoStateModel model) {
        SandboxView view = new SandboxView();
        new SandboxController(model, view);
        setRoot(view);
    }

    private void setRoot(Parent root) {
        if (scene == null) {
            scene = new Scene(root);
            stage.setScene(scene);
        } else {
            scene.setRoot(root);
        }
    }
}
