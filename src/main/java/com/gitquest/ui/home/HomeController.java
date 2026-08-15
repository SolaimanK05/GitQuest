package com.gitquest.ui.home;

import com.gitquest.ui.common.Navigator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;

/** Controller for {@code HomeView.fxml} — the app's true entry point per CLAUDE.md 4.4. */
public final class HomeController {

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
    }
}
