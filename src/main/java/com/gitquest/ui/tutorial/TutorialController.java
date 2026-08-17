package com.gitquest.ui.tutorial;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.core.campaign.LevelSessionFactory;
import com.gitquest.core.campaign.TutorialStep;
import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.GraphDiffCalculator;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.service.CommandService;
import com.gitquest.core.service.RepositorySessionFactory;
import com.gitquest.core.service.TempDirCleanup;
import com.gitquest.persistence.CampaignProgressStore;
import com.gitquest.ui.common.ErrorDialogs;
import com.gitquest.ui.common.Navigator;
import com.gitquest.ui.sandbox.CommitGraphView;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;

/**
 * Controller for {@code TutorialView.fxml} — the real, animated walkthrough a Campaign level shows
 * before handing the player the graded objective (CLAUDE.md 4.2 follow-up). Each
 * {@link TutorialStep}'s action runs against its own throwaway temp repo (never the level's actual
 * starting state) and is animated on the same {@link CommitGraphView} the rest of the app uses —
 * the concept being taught is demonstrated actually happening, not illustrated with static images.
 * Once the last step finishes (or the player skips), the throwaway repo is discarded and the
 * level's real graded session is built via {@link LevelSessionFactory}, exactly as it always was.
 */
public final class TutorialController {

    @FXML
    private Label arcLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label progressLabel;
    @FXML
    private Label narrationLabel;
    @FXML
    private CommitGraphView commitGraphView;
    @FXML
    private Button nextButton;
    @FXML
    private Button skipButton;

    private final CommandService commandService = new CommandService();
    private final CampaignProgressStore progressStore = new CampaignProgressStore();
    private Navigator navigator;
    private LevelDefinition level;
    private RepoStateModel tutorialModel;
    private CommandExecutor tutorialExecutor;
    private int currentStepIndex = -1;

    @FXML
    private void initialize() {
        nextButton.setOnAction(e -> handleNext());
        skipButton.setOnAction(e -> beginChallenge());
    }

    /** Called by {@code SceneRouter} right after load — sets up this level's own throwaway tutorial repo, then shows step 1. */
    public void start(LevelDefinition level, Navigator navigator) {
        this.level = level;
        this.navigator = navigator;
        titleLabel.setText(level.title());
        arcLabel.setText(level.arcId().toUpperCase());
        setBusy(true);

        commandService.submit(() -> {
                    Path tempDir = Files.createTempDirectory("gitquest-tutorial-" + level.id() + "-");
                    RepoStateModel model = RepositorySessionFactory.init(tempDir);
                    model.markDisposable(List.of(tempDir), false);
                    return model;
                },
                model -> {
                    tutorialModel = model;
                    tutorialExecutor = new CommandExecutor(model);
                    commitGraphView.renderInitial(model.snapshot().commits());
                    setBusy(false);
                    goToStep(0);
                },
                error -> {
                    setBusy(false);
                    ErrorDialogs.show("Couldn't start tutorial", error);
                });
    }

    private void handleNext() {
        if (currentStepIndex + 1 < level.tutorial().size()) {
            goToStep(currentStepIndex + 1);
        } else {
            beginChallenge();
        }
    }

    private void goToStep(int index) {
        currentStepIndex = index;
        TutorialStep step = level.tutorial().get(index);
        int total = level.tutorial().size();
        progressLabel.setText("Step " + (index + 1) + " of " + total);
        nextButton.setText(index == total - 1 ? "Start Challenge" : "Next");
        setBusy(true);

        RepoSnapshot before = tutorialModel.snapshot();
        commandService.submit(() -> {
                    step.action().run(tutorialModel, tutorialExecutor);
                    tutorialModel.refresh();
                    return null;
                },
                ignored -> {
                    RepoSnapshot after = tutorialModel.snapshot();
                    GraphDiff diff = GraphDiffCalculator.diff(before, after);
                    commitGraphView.animateDiff(diff);
                    commitGraphView.syncRefsAndHead(after.commits(), after.headRefName(), after.headCommitId());
                    narrationLabel.setText(step.narration());
                    setBusy(false);
                },
                error -> {
                    setBusy(false);
                    ErrorDialogs.show("Tutorial step failed", error);
                });
    }

    /**
     * Discards the throwaway tutorial repo, marks this level's tutorial as watched (so Play won't
     * auto-show it again — re-watching from here on is the dedicated Tutorial button's job), and
     * builds the level's real graded session — same destination whether the tutorial finished or
     * was skipped.
     */
    private void beginChallenge() {
        setBusy(true);
        RepoStateModel finishedTutorialModel = tutorialModel;
        commandService.submit(() -> {
                    List<Path> tutorialPaths = finishedTutorialModel.disposablePaths();
                    finishedTutorialModel.close();
                    TempDirCleanup.deleteAll(tutorialPaths);
                    CampaignProgress progress = progressStore.load();
                    progress.markTutorialWatched(level.id());
                    progressStore.save(progress);
                    return LevelSessionFactory.buildObjectiveSession(level);
                },
                model -> navigator.showSandboxForLevel(model, level),
                error -> {
                    setBusy(false);
                    ErrorDialogs.show("Couldn't start level", error);
                });
    }

    private void setBusy(boolean busy) {
        nextButton.setDisable(busy);
        skipButton.setDisable(busy);
    }
}
