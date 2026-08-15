package com.gitquest.app;

import javafx.application.Application;
import javafx.stage.Stage;

public final class GitQuestApp extends Application {

    @Override
    public void start(Stage stage) {
        SceneRouter router = new SceneRouter(stage);
        router.showEntry();
        stage.setTitle("GitQuest");
        stage.setWidth(1100);
        stage.setHeight(720);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
