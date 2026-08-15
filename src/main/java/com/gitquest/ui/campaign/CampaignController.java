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

/** Controller for {@code CampaignView.fxml} — the skill tree, rendered as a git-graph-style spine, gated by arc completion. */
public final class CampaignController {

    @FXML
    private Button backButton;
    @FXML
    private SkillTreeView skillTreeView;

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
        skillTreeView.render(CampaignCatalog.arcs(), progress, this::startLevel);
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
