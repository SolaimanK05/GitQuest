package com.gitquest.ui.home;

import com.gitquest.core.campaign.CampaignCatalog;
import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.persistence.CampaignProgressStore;
import com.gitquest.ui.common.Navigator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;

/** Controller for {@code HomeView.fxml} — the app's true entry point per CLAUDE.md 4.4. */
public final class HomeController {

    @FXML
    private Label progressLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Button campaignButton;
    @FXML
    private Button sandboxButton;

    private Navigator navigator;

    @FXML
    private void initialize() {
        campaignButton.setOnAction(e -> navigator.showCampaign());
        sandboxButton.setOnAction(e -> navigator.showEntry());
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
        refreshProgressSummary();
    }

    private void refreshProgressSummary() {
        CampaignProgress progress = new CampaignProgressStore().load();
        int completed = progress.completedCount();
        int total = CampaignCatalog.totalLevelCount();
        progressLabel.setText("Campaign: " + completed + " / " + total + " levels complete");
        progressBar.setProgress(total == 0 ? 0 : (double) completed / total);
    }
}
