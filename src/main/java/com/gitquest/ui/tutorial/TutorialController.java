package com.gitquest.ui.tutorial;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.core.campaign.LevelSessionFactory;
import com.gitquest.core.campaign.TutorialStep;
import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.CommitNode;
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
import com.gitquest.ui.common.LoadingIndicator;
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
    private LoadingIndicator tutorialLoadingIndicator;
    @FXML
    private Label commandLabel;
    @FXML
    private Label narrationLabel;
    @FXML
    private CommitGraphView commitGraphView;
    @FXML
    private Button backButton;
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
        backButton.setOnAction(e -> handleBack());
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
        // Building the throwaway tutorial repo is async (real disk I/O) and it starts out with
        // zero commits (RepositorySessionFactory.init() is a bare "git init", nothing more) --
        // step 0's own action is usually what actually populates it, sometimes with several
        // commits at once (e.g. "trigger a conflict" sets up a whole branch-and-merge scenario in
        // its first step). Revealing the graph too early -- right after init, or partway through
        // that first action -- means the enclosing StackPane centers around whatever (possibly
        // empty, possibly not-yet-final) size the view happens to have at that moment, then visibly
        // recenters again once the real final size is known. Staying hidden until goToStep(0) has
        // fully applied step 0's own result is what skips every one of those wrongly-centered
        // layout passes rather than just the first.
        commitGraphView.setVisible(false);

        commandService.submit(() -> {
                    Path tempDir = Files.createTempDirectory("gitquest-tutorial-" + level.id() + "-");
                    RepoStateModel model = RepositorySessionFactory.init(tempDir);
                    model.markDisposable(List.of(tempDir), false);
                    return model;
                },
                model -> {
                    tutorialModel = model;
                    tutorialExecutor = new CommandExecutor(model);
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
                    List<ObjectId> mergeHeads = tutorialModel.getRepository().readMergeHeads();
                    return mergeHeads != null && !mergeHeads.isEmpty() ? mergeHeads.get(0) : null;
                },
                theirsId -> {
                    RepoSnapshot after = tutorialModel.snapshot();
                    GraphDiff diff = GraphDiffCalculator.diff(before, after);
                    commitGraphView.animateDiff(diff);
                    commitGraphView.setVisible(true);
                    commitGraphView.syncRefsAndHead(after.commits(), after.headRefName(), after.headCommitId());
                    updatePendingMergeVisual(after, theirsId);
                    narrationLabel.setText(step.narration());
                    boolean hasCommand = step.command() != null && !step.command().isBlank();
                    commandLabel.setText(hasCommand ? "$ " + step.command() : "");
                    commandLabel.setVisible(hasCommand);
                    commandLabel.setManaged(hasCommand);
                    setBusy(false);
                },
                error -> {
                    setBusy(false);
                    ErrorDialogs.show("Tutorial step failed", error);
                });
    }

    /** A pending (conflicted) merge gets the same dashed, pulsing placeholder here as in Sandbox — mirrors {@code SandboxController.updatePendingMergeVisual}. */
    private void updatePendingMergeVisual(RepoSnapshot after, ObjectId theirsId) {
        if (theirsId == null) {
            commitGraphView.clearPendingMerge();
            return;
        }
        CommitNode ours = after.commits().stream()
                .filter(c -> c.id().equals(after.headCommitId())).findFirst().orElse(null);
        CommitNode theirs = after.commits().stream()
                .filter(c -> c.id().equals(theirsId)).findFirst().orElse(null);
        if (ours != null && theirs != null) {
            commitGraphView.showPendingMerge(ours, theirs);
        } else {
            commitGraphView.clearPendingMerge();
        }
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

    /**
     * Leaves the tutorial without touching the level's completion/tutorial-watched record at all —
     * unlike {@link #beginChallenge()}, bailing out early shouldn't count as having watched it, so
     * Play will still show the animated walkthrough again next time. Just discards the throwaway
     * tutorial repo and heads back to the skill tree.
     */
    private void handleBack() {
        setBusy(true);
        RepoStateModel modelToDiscard = tutorialModel;
        commandService.submit(() -> {
                    if (modelToDiscard == null) {
                        return null;
                    }
                    List<Path> tutorialPaths = modelToDiscard.disposablePaths();
                    modelToDiscard.close();
                    TempDirCleanup.deleteAll(tutorialPaths);
                    return null;
                },
                ignored -> navigator.showCampaign(),
                error -> navigator.showCampaign()); // best-effort cleanup either way -- still leave
    }

    private void setBusy(boolean busy) {
        backButton.setDisable(busy);
        nextButton.setDisable(busy);
        skipButton.setDisable(busy);
        tutorialLoadingIndicator.setActive(busy);
    }
}
