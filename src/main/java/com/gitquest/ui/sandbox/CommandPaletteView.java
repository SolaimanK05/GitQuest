package com.gitquest.ui.sandbox;

import com.gitquest.ui.common.TooltipHelper;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.VBox;

/** Command palette per CLAUDE.md 4.3: buttons for the core Sandbox command set, each with a hover tooltip. */
public final class CommandPaletteView extends VBox {

    private final Button stageButton = new Button("Stage All");
    private final Button commitButton = new Button("Commit...");
    private final Button refreshButton = new Button("Refresh / Log");
    private final Button createBranchButton = new Button("New Branch...");
    private final ChoiceBox<String> checkoutTargetChoice = new ChoiceBox<>();
    private final Button checkoutButton = new Button("Checkout");
    private final ChoiceBox<String> mergeSourceChoice = new ChoiceBox<>();
    private final CheckBox noFastForwardCheck = new CheckBox("No fast-forward");
    private final Button mergeButton = new Button("Merge");

    public CommandPaletteView() {
        setSpacing(12);
        setPadding(new Insets(16));
        setPrefWidth(260);

        TooltipHelper.install(stageButton, "git add . — stage all working-tree changes for the next commit.");
        TooltipHelper.install(commitButton, "git commit — record staged changes as a new commit.");
        TooltipHelper.install(refreshButton, "Re-read the repository from disk (useful after editing files outside GitQuest).");
        TooltipHelper.install(createBranchButton, "git branch <name> — create a new branch pointing at HEAD.");
        TooltipHelper.install(checkoutTargetChoice, "Pick a branch to switch to.");
        TooltipHelper.install(checkoutButton, "git checkout <branch> — switch HEAD and the working tree to that branch.");
        TooltipHelper.install(mergeSourceChoice, "Pick a branch to merge into the current branch.");
        TooltipHelper.install(noFastForwardCheck, "Always create a merge commit, even when a fast-forward is possible.");
        TooltipHelper.install(mergeButton, "git merge <branch> — merge the selected branch into the current branch.");

        for (var control : new javafx.scene.control.Control[] {
                stageButton, commitButton, refreshButton, createBranchButton,
                checkoutTargetChoice, checkoutButton, mergeSourceChoice, mergeButton
        }) {
            control.setMaxWidth(Double.MAX_VALUE);
        }

        getChildren().addAll(
                sectionLabel("Working tree"),
                stageButton,
                commitButton,
                refreshButton,
                new Separator(),
                sectionLabel("Branching"),
                createBranchButton,
                checkoutTargetChoice,
                checkoutButton,
                new Separator(),
                sectionLabel("Merging"),
                mergeSourceChoice,
                noFastForwardCheck,
                mergeButton);
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    public Button getStageButton() {
        return stageButton;
    }

    public Button getCommitButton() {
        return commitButton;
    }

    public Button getRefreshButton() {
        return refreshButton;
    }

    public Button getCreateBranchButton() {
        return createBranchButton;
    }

    public ChoiceBox<String> getCheckoutTargetChoice() {
        return checkoutTargetChoice;
    }

    public Button getCheckoutButton() {
        return checkoutButton;
    }

    public ChoiceBox<String> getMergeSourceChoice() {
        return mergeSourceChoice;
    }

    public CheckBox getNoFastForwardCheck() {
        return noFastForwardCheck;
    }

    public Button getMergeButton() {
        return mergeButton;
    }
}
