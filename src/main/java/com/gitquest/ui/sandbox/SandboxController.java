package com.gitquest.ui.sandbox;

import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.command.StatusSnapshot;
import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.CommandService;
import com.gitquest.core.service.WorkingTreeWatcher;
import com.gitquest.ui.common.ErrorDialogs;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Controller for {@code SandboxView.fxml}. {@code FXMLLoader} instantiates
 * this via a no-arg constructor and injects the {@code @FXML} fields before
 * calling {@link #initialize()} — {@link #setModel(RepoStateModel)} is
 * called separately by {@code SceneRouter} right after load (before the
 * scene is shown), since the repo session isn't available at
 * FXML-instantiation time. {@code initialize()} only wires things that
 * don't need {@code model}/{@code commandExecutor} yet; button handlers
 * read those fields at click-time, by when {@code setModel} has already run.
 */
public final class SandboxController {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final double SIDEBAR_EXPANDED_WIDTH = 280;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 40;
    private static final Duration SIDEBAR_ANIMATION = Duration.millis(220);

    @FXML
    private VBox sidebarContainer;
    @FXML
    private Button collapseSidebarButton;
    @FXML
    private VBox sidebarContent;
    @FXML
    private TreeView<Path> fileTree;
    @FXML
    private ToggleButton terminalToggleButton;
    @FXML
    private Accordion commandAccordion;
    @FXML
    private HBox terminalInputBox;
    @FXML
    private TextField terminalInputField;
    @FXML
    private CommitGraphView commitGraphView;
    @FXML
    private Label branchLabel;
    @FXML
    private Label headLabel;
    @FXML
    private Label dirtyLabel;
    @FXML
    private ListView<String> commandLog;
    @FXML
    private Button stageButton;
    @FXML
    private Button commitButton;
    @FXML
    private Button refreshButton;
    @FXML
    private Button createBranchButton;
    @FXML
    private ChoiceBox<String> checkoutTargetChoice;
    @FXML
    private Button checkoutButton;
    @FXML
    private ChoiceBox<String> mergeSourceChoice;
    @FXML
    private CheckBox noFastForwardCheck;
    @FXML
    private Button mergeButton;

    private final CommandService commandService = new CommandService();
    private RepoStateModel model;
    private CommandExecutor commandExecutor;
    private Path repoRoot;
    private volatile StatusSnapshot latestStatus;
    private boolean sidebarCollapsed;
    private WorkingTreeWatcher workingTreeWatcher;

    @FXML
    private void initialize() {
        stageButton.setOnAction(e -> run("git add .", () -> commandExecutor.stageAll()));
        commitButton.setOnAction(e -> handleCommit());
        refreshButton.setOnAction(e -> handleRefresh());
        createBranchButton.setOnAction(e -> handleCreateBranch());
        checkoutButton.setOnAction(e -> handleCheckout());
        mergeButton.setOnAction(e -> handleMerge());

        collapseSidebarButton.setOnAction(e -> toggleSidebar());
        terminalToggleButton.setOnAction(e -> toggleTerminal());
        terminalInputField.setOnAction(e -> handleTerminalCommand());
    }

    /** Finishes wiring once the repo session is known — see class javadoc. */
    public void setModel(RepoStateModel model) {
        this.model = model;
        this.commandExecutor = new CommandExecutor(model);
        this.repoRoot = model.getRepository().getWorkTree().toPath();

        fileTree.setCellFactory(FileTreeBuilder.cellFactory(repoRoot, () -> latestStatus));
        refreshFileTree();

        RepoSnapshot initial = model.snapshot();
        commitGraphView.renderInitial(initial.commits());
        commitGraphView.syncRefsAndHead(initial.commits(), initial.headRefName(), initial.headCommitId());
        refreshBranchChoices(initial);
        refreshHeadAndBranchLabels(initial);
        refreshDirtyCount();

        // Per CLAUDE.md 4.3: no built-in editor, so pick up edits made in the
        // user's own editor by watching the working directory instead of
        // requiring a manual Refresh click. Never touches the commit graph —
        // uncommitted edits are working-tree state, not a graph node.
        workingTreeWatcher = new WorkingTreeWatcher(repoRoot, java.time.Duration.ofMillis(400), this::onWorkingTreeChanged);
        workingTreeWatcher.start();
    }

    /** Runs on the watcher's background thread — hop to the FX thread before touching UI state. */
    private void onWorkingTreeChanged() {
        TreeItem<Path> newTree = FileTreeBuilder.buildTree(repoRoot);
        Platform.runLater(() -> fileTree.setRoot(newTree));
        refreshDirtyCount();
    }

    // ---- sidebar / terminal chrome ----

    private void toggleSidebar() {
        boolean expanding = sidebarCollapsed;
        double target = expanding ? SIDEBAR_EXPANDED_WIDTH : SIDEBAR_COLLAPSED_WIDTH;
        if (expanding) {
            sidebarContent.setVisible(true);
            sidebarContent.setManaged(true);
        }
        Timeline timeline = new Timeline(new KeyFrame(SIDEBAR_ANIMATION,
                new KeyValue(sidebarContainer.prefWidthProperty(), target, Interpolator.EASE_BOTH)));
        timeline.setOnFinished(e -> {
            sidebarCollapsed = !expanding;
            if (!expanding) {
                sidebarContent.setVisible(false);
                sidebarContent.setManaged(false);
            }
        });
        // Arrow always points the direction this click just moved the sidebar,
        // hinting at the *next* click's effect (collapse vs. expand).
        collapseSidebarButton.setText(expanding ? "❮" : "❯");
        timeline.play();
    }

    private void toggleTerminal() {
        boolean showTerminal = terminalToggleButton.isSelected();
        terminalInputBox.setVisible(showTerminal);
        terminalInputBox.setManaged(showTerminal);
        if (showTerminal) {
            terminalInputField.requestFocus();
        }
    }

    private void handleTerminalCommand() {
        String raw = terminalInputField.getText();
        if (raw == null || raw.isBlank()) {
            return;
        }
        terminalInputField.clear();
        String trimmed = raw.trim();
        String verb = trimmed.split("\\s+", 2)[0].toLowerCase();

        if (verb.equals("status")) {
            logLine(trimmed);
            commandService.submit(commandExecutor::status,
                    status -> logLine("  " + status.dirtyFileCount() + " dirty file(s)"),
                    error -> logLine("  ✗ " + rootMessage(error)));
            return;
        }
        if (verb.equals("refresh") || verb.equals("log")) {
            runRefresh(trimmed);
            return;
        }
        run(trimmed, () -> TerminalCommandParser.execute(trimmed, commandExecutor));
    }

    // ---- button handlers ----

    private void handleCommit() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Commit");
        dialog.setHeaderText("Commit message");
        Optional<String> message = dialog.showAndWait();
        message.filter(text -> !text.isBlank())
                .ifPresent(text -> run("git commit -m \"" + text + "\"",
                        () -> commandExecutor.commit(text, "GitQuest User", "gitquest@example.com")));
    }

    private void handleRefresh() {
        runRefresh("refresh (re-read repository from disk)");
    }

    private void handleCreateBranch() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("New Branch");
        dialog.setHeaderText("Branch name");
        Optional<String> name = dialog.showAndWait();
        name.filter(text -> !text.isBlank())
                .ifPresent(text -> run("git branch " + text, () -> commandExecutor.createBranch(text)));
    }

    private void handleCheckout() {
        String target = checkoutTargetChoice.getValue();
        if (target != null) {
            run("git checkout " + target, () -> commandExecutor.checkout(target));
        }
    }

    private void handleMerge() {
        String source = mergeSourceChoice.getValue();
        if (source == null) {
            return;
        }
        boolean noFastForward = noFastForwardCheck.isSelected();
        String description = "git merge " + source + (noFastForward ? " --no-ff" : "");
        run(description, () -> commandExecutor.merge(source, noFastForward));
    }

    // ---- shared dispatch ----

    private void run(String description, Callable<GraphDiff> work) {
        logLine(description);
        setBusy(true);
        commandService.submit(work, this::onCommandSucceeded, this::onError);
    }

    private void runRefresh(String description) {
        logLine(description);
        setBusy(true);
        commandService.submit(() -> {
            model.refresh();
            return model.snapshot();
        }, this::onManualRefresh, this::onError);
    }

    private void onCommandSucceeded(GraphDiff diff) {
        setBusy(false);
        logLine("  ✓ done");
        commitGraphView.animateDiff(diff);
        RepoSnapshot snapshot = model.snapshot();
        commitGraphView.syncRefsAndHead(snapshot.commits(), snapshot.headRefName(), snapshot.headCommitId());
        refreshBranchChoices(snapshot);
        refreshHeadAndBranchLabels(snapshot);
        refreshDirtyCount();
        refreshFileTree();
    }

    private void onManualRefresh(RepoSnapshot snapshot) {
        setBusy(false);
        logLine("  ✓ done");
        commitGraphView.renderInitial(snapshot.commits());
        commitGraphView.syncRefsAndHead(snapshot.commits(), snapshot.headRefName(), snapshot.headCommitId());
        refreshBranchChoices(snapshot);
        refreshHeadAndBranchLabels(snapshot);
        refreshDirtyCount();
        refreshFileTree();
    }

    private void onError(Throwable error) {
        setBusy(false);
        logLine("  ✗ failed: " + rootMessage(error));
        ErrorDialogs.show("Command failed", error);
    }

    private void logLine(String text) {
        var items = commandLog.getItems();
        items.add("[" + LocalTime.now().format(LOG_TIME) + "] " + text);
        commandLog.scrollTo(items.size() - 1);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    // ---- side-panel refreshes ----

    private void refreshFileTree() {
        fileTree.setRoot(FileTreeBuilder.buildTree(repoRoot));
    }

    private void refreshBranchChoices(RepoSnapshot snapshot) {
        List<String> names = snapshot.branches().stream().map(BranchRef::name).collect(Collectors.toList());
        String currentCheckout = checkoutTargetChoice.getValue();
        String currentMerge = mergeSourceChoice.getValue();
        checkoutTargetChoice.getItems().setAll(names);
        mergeSourceChoice.getItems().setAll(names);
        if (names.contains(currentCheckout)) {
            checkoutTargetChoice.setValue(currentCheckout);
        }
        if (names.contains(currentMerge)) {
            mergeSourceChoice.setValue(currentMerge);
        }
    }

    private void refreshHeadAndBranchLabels(RepoSnapshot snapshot) {
        String branchName = snapshot.headRefName() != null ? snapshot.headRefName() : "(detached HEAD)";
        branchLabel.setText("Branch: " + branchName);
        ObjectId head = snapshot.headCommitId();
        headLabel.setText("HEAD: " + (head != null ? head.abbreviate(7).name() : "-"));
    }

    private void refreshDirtyCount() {
        commandService.submit(commandExecutor::status,
                status -> {
                    latestStatus = status;
                    dirtyLabel.setText("Dirty files: " + status.dirtyFileCount());
                    fileTree.refresh();
                },
                error -> dirtyLabel.setText("Dirty files: -"));
    }

    private void setBusy(boolean busy) {
        commandAccordion.setDisable(busy);
        terminalInputField.setDisable(busy);
    }
}
