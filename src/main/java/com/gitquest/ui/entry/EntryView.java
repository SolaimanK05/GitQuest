package com.gitquest.ui.entry;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/** Three equally-weighted ways to start a session: Clone, Open, Initialize. See CLAUDE.md 4.1. */
public final class EntryView extends BorderPane {

    private final TextField cloneUrlField = new TextField();
    private final Button cloneButton = new Button("Clone...");
    private final Button openButton = new Button("Open...");
    private final Button initializeButton = new Button("Initialize...");

    public EntryView() {
        Label title = new Label("GitQuest");
        title.setFont(Font.font(null, FontWeight.BOLD, 28));

        VBox sections = new VBox(24, cloneSection(), openSection(), initializeSection());
        sections.setMaxWidth(480);
        sections.setAlignment(Pos.TOP_CENTER);

        VBox content = new VBox(32, title, sections);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(48));

        setCenter(content);
    }

    private VBox cloneSection() {
        cloneUrlField.setPromptText("https://github.com/example/repo.git");
        HBox row = new HBox(8, cloneUrlField, cloneButton);
        row.setAlignment(Pos.CENTER_LEFT);
        cloneUrlField.setPrefWidth(320);
        return section("Clone", "Clone a remote repository to a local folder.", row);
    }

    private VBox openSection() {
        HBox row = new HBox(8, openButton);
        return section("Open", "Open an existing local folder. If it's not a Git repository yet, you'll be offered the chance to initialize it.", row);
    }

    private VBox initializeSection() {
        HBox row = new HBox(8, initializeButton);
        return section("Initialize", "Start a brand new repository from scratch.", row);
    }

    private VBox section(String heading, String description, HBox controls) {
        Label headingLabel = new Label(heading);
        headingLabel.setFont(Font.font(null, FontWeight.SEMI_BOLD, 16));
        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        VBox box = new VBox(6, headingLabel, descriptionLabel, controls);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }

    public TextField getCloneUrlField() {
        return cloneUrlField;
    }

    public Button getCloneButton() {
        return cloneButton;
    }

    public Button getOpenButton() {
        return openButton;
    }

    public Button getInitializeButton() {
        return initializeButton;
    }
}
