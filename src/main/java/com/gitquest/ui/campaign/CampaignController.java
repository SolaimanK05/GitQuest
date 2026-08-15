package com.gitquest.ui.campaign;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.gitquest.core.campaign.CampaignCatalog;
import com.gitquest.core.campaign.CampaignCatalog.ArcInfo;
import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.CommandService;
import com.gitquest.core.service.RepositorySessionFactory;
import com.gitquest.persistence.CampaignProgressStore;
import com.gitquest.ui.common.ErrorDialogs;
import com.gitquest.ui.common.Navigator;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

/** Controller for {@code CampaignView.fxml} — the skill tree, grouped by arc, gated by arc completion. */
public final class CampaignController {

    @FXML
    private Button backButton;
    @FXML
    private VBox arcsContainer;

    private final CampaignProgressStore progressStore = new CampaignProgressStore();
    private final CommandService commandService = new CommandService();
    private CampaignProgress progress;
    private Navigator navigator;

    @FXML
    private void initialize() {
        backButton.setOnAction(e -> navigator.showHome());
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        this.progress = progressStore.load();
        render();
    }

    private void render() {
        arcsContainer.getChildren().clear();
        for (ArcInfo arc : CampaignCatalog.arcs()) {
            arcsContainer.getChildren().add(buildArcSection(arc));
        }
    }

    private VBox buildArcSection(ArcInfo arc) {
        boolean unlocked = progress.isArcUnlocked(arc.id());
        List<LevelDefinition> levels = CampaignCatalog.levelsForArc(arc.id());

        Label title = new Label((unlocked ? "" : "🔒 ") + arc.title());
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");
        Label description = new Label(arc.description());
        description.getStyleClass().add("sub-label");
        description.setWrapText(true);

        FlowPane cardRow = new FlowPane(12, 12);

        if (levels.isEmpty()) {
            Label placeholder = new Label("Coming soon");
            placeholder.getStyleClass().add("sub-label");
            cardRow.getChildren().add(placeholder);
        } else {
            for (LevelDefinition level : levels) {
                cardRow.getChildren().add(buildLevelCard(level, unlocked));
            }
        }

        VBox section = new VBox(8, title, description, cardRow);
        section.setPadding(new Insets(0, 0, 20, 0));
        return section;
    }

    private Button buildLevelCard(LevelDefinition level, boolean arcUnlocked) {
        boolean completed = progress.isLevelCompleted(level.id());
        String status = completed ? "✓ Completed" : (arcUnlocked ? "▶ Play" : "🔒 Locked");
        Button card = new Button(level.title() + "\n" + status);
        card.getStyleClass().add("level-card");
        if (completed) {
            card.getStyleClass().add("completed");
        }
        card.setDisable(!arcUnlocked);
        card.setWrapText(true);
        card.setPrefSize(160, 80);
        card.setOnAction(e -> startLevel(level));
        return card;
    }

    private void startLevel(LevelDefinition level) {
        commandService.submit(() -> {
            Path tempDir = Files.createTempDirectory("gitquest-level-" + level.id());
            RepoStateModel model = RepositorySessionFactory.init(tempDir);
            CommandExecutor executor = new CommandExecutor(model);
            level.setup().build(model, executor);
            return model;
        }, model -> navigator.showSandboxForLevel(model, level),
                error -> ErrorDialogs.show("Couldn't start level", error));
    }
}
