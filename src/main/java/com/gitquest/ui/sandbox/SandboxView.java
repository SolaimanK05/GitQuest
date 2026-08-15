package com.gitquest.ui.sandbox;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Sandbox screen layout: command palette + animated commit graph + status bar + command log, per CLAUDE.md 4.3. */
public final class SandboxView extends BorderPane {

    private final CommandPaletteView commandPalette = new CommandPaletteView();
    private final CommitGraphView commitGraphView = new CommitGraphView();
    private final Label branchLabel = new Label();
    private final Label headLabel = new Label();
    private final Label dirtyLabel = new Label();
    private final ListView<String> commandLog = new ListView<>();

    public SandboxView() {
        ScrollPane scrollPane = new ScrollPane(commitGraphView);
        scrollPane.setFitToWidth(false);
        scrollPane.setFitToHeight(false);

        HBox statusBar = new HBox(24, branchLabel, headLabel, dirtyLabel);
        statusBar.setPadding(new Insets(8, 16, 8, 16));
        statusBar.setStyle("-fx-background-color: #F2F4F7;");

        commandLog.setPrefHeight(140);
        commandLog.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        VBox bottom = new VBox(statusBar, commandLog);

        setLeft(commandPalette);
        setCenter(scrollPane);
        setBottom(bottom);
    }

    public CommandPaletteView getCommandPalette() {
        return commandPalette;
    }

    public CommitGraphView getCommitGraphView() {
        return commitGraphView;
    }

    public Label getBranchLabel() {
        return branchLabel;
    }

    public Label getHeadLabel() {
        return headLabel;
    }

    public Label getDirtyLabel() {
        return dirtyLabel;
    }

    public ListView<String> getCommandLog() {
        return commandLog;
    }
}
