package com.gitquest.ui.campaign;

import java.nio.file.Files;
import java.nio.file.Path;

import com.gitquest.core.campaign.CampaignCatalog;
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
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controller for {@code CampaignView.fxml} — the skill tree (rendered as a
 * git-graph-style chain, gated by strict level-by-level progression) on the
 * left, and a detail tab on the right. Clicking a node in the tree only
 * selects it and populates the detail tab; starting the level happens via
 * that tab's Play button, not the node itself.
 */
public final class CampaignController {

    @FXML
    private Button backButton;
    @FXML
    private SkillTreeView skillTreeView;
    @FXML
    private Label detailArcLabel;
    @FXML
    private Label detailTitleLabel;
    @FXML
    private Label detailDescriptionLabel;
    @FXML
    private Button playButton;

    private final CampaignProgressStore progressStore = new CampaignProgressStore();
    private final CommandService commandService = new CommandService();
    private CampaignProgress progress;
    private Navigator navigator;

    @FXML
    private void initialize() {
        backButton.setOnAction(e -> navigator.showHome());
        showNoSelection();
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        this.progress = progressStore.load();
        skillTreeView.render(CampaignCatalog.arcs(), progress, this::selectLevel);
    }

    private void showNoSelection() {
        detailArcLabel.setText("");
        detailTitleLabel.setText("Select a level");
        detailDescriptionLabel.setText("Click any node in the tree to see its objective and start it.");
        playButton.setDisable(true);
        playButton.setOnAction(null);
    }

    private void selectLevel(LevelDefinition level) {
        boolean completed = progress.isLevelCompleted(level.id());
        CampaignCatalog.arcs().stream()
                .filter(arc -> arc.id().equals(level.arcId()))
                .findFirst()
                .ifPresent(arc -> detailArcLabel.setText(arc.title().toUpperCase()));
        detailTitleLabel.setText(level.title());
        detailDescriptionLabel.setText(level.objective());
        playButton.setText(completed ? "Play Again" : "Play");
        playButton.setDisable(false);
        playButton.setOnAction(e -> startLevel(level));
    }

    private void startLevel(LevelDefinition level) {
        commandService.submit(() -> {
            Path tempDir = Files.createTempDirectory("gitquest-level-" + level.id());
            RepoStateModel model = RepositorySessionFactory.init(tempDir);
            CommandExecutor executor = new CommandExecutor(model);
            level.setup().build(model, executor);
            model.markDisposable(java.util.List.of(tempDir), false);
            return model;
        }, model -> navigator.showSandboxForLevel(model, level),
                error -> ErrorDialogs.show("Couldn't start level", error));
    }
}
