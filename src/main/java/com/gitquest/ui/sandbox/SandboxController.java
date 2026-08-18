package com.gitquest.ui.sandbox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.assistant.ChatMessage;
import com.gitquest.core.assistant.GeminiClient;
import com.gitquest.core.assistant.GeminiConfig;
import com.gitquest.core.assistant.RepoContextSummary;
import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.core.codegraph.DependencyEdge;
import com.gitquest.core.codegraph.HistoricalJavaSourceReader;
import com.gitquest.core.codegraph.JavaDependencyAnalyzer;
import com.gitquest.core.codegraph.JavaDependencyGraph;
import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.command.StatusSnapshot;
import com.gitquest.core.conflict.ConflictDiffReader;
import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.CommandService;
import com.gitquest.core.service.FileTreeCopy;
import com.gitquest.core.service.RemoteRefPoller;
import com.gitquest.core.service.TempDirCleanup;
import com.gitquest.core.service.WorkingTreeWatcher;
import com.gitquest.persistence.CampaignProgressStore;
import com.gitquest.ui.common.ErrorDialogs;
import com.gitquest.ui.common.Navigator;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
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
    private static final DateTimeFormatter SCRUBBER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final double SIDEBAR_EXPANDED_WIDTH = 280;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 40;
    private static final Duration SIDEBAR_ANIMATION = Duration.millis(220);

    @FXML
    private VBox sidebarContainer;
    @FXML
    private Button collapseSidebarButton;
    @FXML
    private Button homeButton;
    @FXML
    private Button openSandboxFolderButton;
    @FXML
    private VBox sidebarContent;
    @FXML
    private TreeView<Path> fileTree;
    @FXML
    private Label commandsHeaderLabel;
    @FXML
    private Accordion commandAccordion;
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
    private Label remoteNoticeLabel;
    @FXML
    private Button fetchNowButton;
    @FXML
    private Button checkRemoteButton;
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
    @FXML
    private Button undoLastCommandButton;
    @FXML
    private TabPane mainTabPane;
    @FXML
    private Tab assistantTab;
    @FXML
    private ScrollPane assistantScrollPane;
    @FXML
    private VBox assistantMessagesBox;
    @FXML
    private Label assistantAttachedFileLabel;
    @FXML
    private Label assistantStatusLabel;
    @FXML
    private TextField assistantInputField;
    @FXML
    private Button assistantSendButton;
    @FXML
    private Tab codeGraphTab;
    @FXML
    private Button analyzeCodeGraphButton;
    @FXML
    private CheckBox codeGraphTier2Check;
    @FXML
    private Label codeGraphStatusLabel;
    @FXML
    private Slider codeGraphTimeTravelSlider;
    @FXML
    private Label codeGraphTimeTravelLabel;
    @FXML
    private ScrollPane codeGraphHost;
    @FXML
    private CodeGraphView codeGraphView;
    @FXML
    private ScrollPane codeGraphDetailPanel;
    @FXML
    private Label codeGraphDetailHeadingLabel;
    @FXML
    private Label codeGraphSelectedPathLabel;
    @FXML
    private Label codeGraphDependsOnHeadingLabel;
    @FXML
    private VBox codeGraphDependsOnBox;
    @FXML
    private Label codeGraphUsedByHeadingLabel;
    @FXML
    private VBox codeGraphUsedByBox;
    @FXML
    private Tab conflictsTab;
    @FXML
    private ListView<String> conflictedFilesList;
    @FXML
    private ConflictDiffView conflictDiffView;
    @FXML
    private Button keepOursButton;
    @FXML
    private Button keepTheirsButton;
    @FXML
    private VBox campaignBanner;
    @FXML
    private Label levelTitleLabel;
    @FXML
    private Label objectiveLabel;
    @FXML
    private VBox campaignChecklistSection;
    @FXML
    private VBox checklistBox;
    @FXML
    private Label whyItMattersLabel;
    @FXML
    private Button backToSkillTreeButton;

    private final CommandService commandService = new CommandService();
    private final CampaignProgressStore progressStore = new CampaignProgressStore();
    private RepoStateModel model;
    private CommandExecutor commandExecutor;
    private Path repoRoot;
    private volatile StatusSnapshot latestStatus;
    private boolean sidebarCollapsed;
    private WorkingTreeWatcher workingTreeWatcher;
    private RemoteRefPoller remotePoller;
    private Navigator navigator;
    private LevelDefinition activeLevel;
    private int hintsUsedThisAttempt;
    private boolean levelCompletedThisSession;
    private boolean conflictWarningShown;
    private List<Rectangle> checklistIndicators = List.of();
    private boolean[] checklistHintExpandedOnce = new boolean[0];

    // ---- code relationship graph state (CLAUDE.md 4.3 Tier 1) ----
    private JavaDependencyGraph currentCodeGraph;
    private boolean codeGraphEverLoaded;
    private List<CommitNode> codeGraphHistoryForScrubber = List.of();
    private int lastRenderedCodeGraphScrubberIndex = -1;

    // ---- Gemini-powered Git tutor (repo-aware; replaces the old Codebase visualizer) ----
    private static final String ASSISTANT_PERSONA =
            "You are the GitQuest Git Tutor, embedded in a desktop app that teaches Git through "
                    + "live animated visualization. Explain Git concepts clearly and concisely for a "
                    + "learner. When it's relevant to the question, ground your answer in the user's "
                    + "actual current sandbox repository state given below, instead of speaking only in "
                    + "the abstract. Prefer short paragraphs or a few bullet points over long essays or "
                    + "markdown tables — this reply renders as plain text, not markdown.";
    private final GeminiClient geminiClient = new GeminiClient();
    private final List<ChatMessage> chatHistory = new ArrayList<>();
    private boolean assistantRequestInFlight;

    @FXML
    private void initialize() {
        stageButton.setOnAction(e -> run("git add .", () -> commandExecutor.stageAll()));
        commitButton.setOnAction(e -> handleCommit());
        refreshButton.setOnAction(e -> handleRefresh());
        createBranchButton.setOnAction(e -> handleCreateBranch());
        checkoutButton.setOnAction(e -> handleCheckout());
        mergeButton.setOnAction(e -> handleMerge());
        undoLastCommandButton.setOnAction(e -> handleUndoLastCommand());

        collapseSidebarButton.setOnAction(e -> toggleSidebar());
        homeButton.setOnAction(e -> leaveSandbox(navigator::showHome));
        openSandboxFolderButton.setOnAction(e -> openSandboxFolder());
        terminalInputField.setOnAction(e -> handleTerminalCommand());

        assistantSendButton.setOnAction(e -> handleSendChatMessage());
        assistantInputField.setOnAction(e -> handleSendChatMessage());
        fileTree.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldItem, newItem) -> updateAssistantAttachedFileLabel(newItem));
        updateAssistantAttachedFileLabel(null);
        if (!GeminiConfig.isConfigured()) {
            assistantStatusLabel.setText("Set the GEMINI_API_KEY environment variable (and restart GitQuest) "
                    + "to enable the AI Git tutor.");
            assistantStatusLabel.setVisible(true);
            assistantStatusLabel.setManaged(true);
            assistantInputField.setDisable(true);
            assistantSendButton.setDisable(true);
        }

        analyzeCodeGraphButton.setOnAction(e -> refreshCodeGraph());
        codeGraphTier2Check.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (isViewingLiveCodeGraph()) {
                lastRenderedCodeGraphScrubberIndex = -1; // force a reload even though the slider value itself didn't change
                onCodeGraphScrubberChanged((int) codeGraphTimeTravelSlider.getValue());
            }
        });
        codeGraphView.setOnNodeSelected(this::showCodeGraphFileDetail);
        codeGraphView.setOnEdgeSelected(this::showCodeGraphEdgeDetail);
        codeGraphTimeTravelSlider.valueProperty().addListener((obs, oldVal, newVal) -> onCodeGraphScrubberChanged(newVal.intValue()));
        conflictedFilesList.getSelectionModel().selectedItemProperty().addListener((obs, oldFile, newFile) -> loadConflictDiff(newFile));
        keepOursButton.setOnAction(e -> resolveSelectedConflict(true));
        keepTheirsButton.setOnAction(e -> resolveSelectedConflict(false));
        fetchNowButton.setOnAction(e -> run("git fetch", commandExecutor::fetch));
        checkRemoteButton.setOnAction(e -> handleCheckRemote());
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (oldTab == codeGraphTab) {
                codeGraphView.stopPhysics(); // pause the simulation while the tab isn't visible
            }
            if (newTab == codeGraphTab) {
                if (!codeGraphEverLoaded) {
                    refreshCodeGraph();
                } else {
                    codeGraphView.resumePhysics();
                }
            }
            if (newTab == conflictsTab) {
                refreshConflictedFilesList();
            }
        });
    }

    /**
     * Lets the "Home" button find its way back regardless of how this screen was entered (plain
     * Sandbox, a Campaign level, or one side of a two-clone Collaboration session) — call before
     * (or via) {@link #setModel}/{@link #setCampaignLevel} finishes wiring the rest of the screen.
     */
    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    /** Used by the two-clone Collaboration Demo: each embedded session's own Home button would only clean up its own clone, not its sibling or their shared local origin — CollabController's single top-level "Home" button owns that instead. */
    public void hideHomeButton() {
        homeButton.setVisible(false);
        homeButton.setManaged(false);
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

        // Passive "origin/main has new commits" notice (CLAUDE.md 4.3) — cheap ls-remote poll,
        // never an auto-fetch/merge. No-ops harmlessly on every tick if no "origin" is configured.
        remotePoller = new RemoteRefPoller(model.getGit(), "origin", java.time.Duration.ofSeconds(20), this::onRemoteRefsChanged);
        remotePoller.start();
    }

    /** Runs on the watcher's background thread — hop to the FX thread before touching UI state. */
    private void onWorkingTreeChanged() {
        TreeItem<Path> newTree = FileTreeBuilder.buildTree(repoRoot);
        Platform.runLater(() -> fileTree.setRoot(newTree));
        refreshDirtyCount();
    }

    // ---- campaign mode ----

    /** Enters this Sandbox screen scoped to a Campaign level instead of free play — see class javadoc. */
    public void setCampaignLevel(RepoStateModel model, LevelDefinition level, Navigator navigator) {
        this.activeLevel = level;
        setNavigator(navigator);
        setModel(model);

        campaignBanner.setVisible(true);
        campaignBanner.setManaged(true);
        levelTitleLabel.setText(level.title());
        objectiveLabel.setText(level.objective());
        whyItMattersLabel.setText("Why it matters: " + level.whyItMatters());
        backToSkillTreeButton.setOnAction(e -> leaveSandbox(navigator::showCampaign));

        buildChecklist(level);

        // Campaign levels are terminal-only — no button palette. Typing the
        // real commands is the point of a *guided* campaign.
        commandsHeaderLabel.setVisible(false);
        commandsHeaderLabel.setManaged(false);
        commandAccordion.setVisible(false);
        commandAccordion.setManaged(false);
        terminalInputField.requestFocus();
        checkLevelGoal(); // covers a level that starts already partway toward its checklist
    }

    /**
     * A concrete objective breakdown, one row per {@link com.gitquest.core.campaign.ChecklistGoal}
     * — a small indicator (filled once satisfied, auto-driven by {@link #checkLevelGoal()}, not
     * user-toggleable) plus its own collapsed {@link TitledPane} hint underneath, instead of one
     * hint dialog covering the whole level.
     */
    private void buildChecklist(LevelDefinition level) {
        checklistBox.getChildren().clear();
        hintsUsedThisAttempt = 0;
        checklistHintExpandedOnce = new boolean[level.checklist().size()];

        List<Rectangle> indicators = new ArrayList<>();
        for (int i = 0; i < level.checklist().size(); i++) {
            var item = level.checklist().get(i);
            int itemIndex = i;

            Rectangle indicator = new Rectangle(13, 13);
            indicator.setArcWidth(4);
            indicator.setArcHeight(4);
            indicator.setFill(Color.TRANSPARENT);
            indicator.setStroke(Color.web("#5B6472"));
            indicator.setStrokeWidth(1.5);
            indicators.add(indicator);

            Label text = new Label(item.describeObjective());
            text.setWrapText(true);
            HBox row = new HBox(8, indicator, text);
            row.setAlignment(Pos.CENTER_LEFT);

            VBox itemBox = new VBox(4, row);
            if (item.hint() != null && !item.hint().isBlank()) {
                Label hintLabel = new Label(item.hint());
                hintLabel.setWrapText(true);
                hintLabel.getStyleClass().add("sub-label");
                TitledPane hintPane = new TitledPane("Hint", hintLabel);
                hintPane.setExpanded(false);
                hintPane.getStyleClass().add("checklist-hint-pane");
                hintPane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                    if (isExpanded && !checklistHintExpandedOnce[itemIndex]) {
                        checklistHintExpandedOnce[itemIndex] = true;
                        hintsUsedThisAttempt++;
                    }
                });
                itemBox.getChildren().add(hintPane);
            }
            checklistBox.getChildren().add(itemBox);
        }

        checklistIndicators = indicators;
        boolean hasChecklist = !checklistIndicators.isEmpty();
        campaignChecklistSection.setVisible(hasChecklist);
        campaignChecklistSection.setManaged(hasChecklist);
    }

    private record LevelProgress(boolean goalSatisfied, List<Boolean> checklistSatisfied) {
    }

    /** Checked off the FX thread — a goal may need real JGit/disk reads (see GitignoreExcludesGoal). */
    private void checkLevelGoal() {
        if (activeLevel == null || levelCompletedThisSession) {
            return;
        }
        LevelDefinition level = activeLevel;
        commandService.submit(() -> new LevelProgress(
                        level.goal().isSatisfied(model),
                        level.checklist().stream().map(item -> item.isSatisfied(model)).toList()),
                progress -> {
                    for (int i = 0; i < checklistIndicators.size() && i < progress.checklistSatisfied().size(); i++) {
                        boolean satisfied = progress.checklistSatisfied().get(i);
                        Rectangle indicator = checklistIndicators.get(i);
                        indicator.setFill(satisfied ? Color.web("#F05133") : Color.TRANSPARENT);
                        indicator.setStroke(satisfied ? Color.web("#F05133") : Color.web("#5B6472"));
                    }
                    if (progress.goalSatisfied()) {
                        onLevelGoalSatisfied();
                    }
                },
                error -> { /* transient check failure; next command will re-check */ });
    }

    private void onLevelGoalSatisfied() {
        levelCompletedThisSession = true;
        CampaignProgress progress = progressStore.load();
        boolean firstTime = !progress.isLevelCompleted(activeLevel.id());
        progress.recordCompletion(activeLevel.id(), hintsUsedThisAttempt);
        progressStore.save(progress);

        Alert done = new Alert(AlertType.INFORMATION,
                firstTime ? "Nice work!" : "Solved again — this level was already completed earlier.",
                ButtonType.OK);
        done.setHeaderText("🎉 " + activeLevel.title() + " complete");
        done.showAndWait();
    }

    // ---- Gemini-powered Git tutor ----

    private void handleSendChatMessage() {
        String text = assistantInputField.getText();
        if (text == null || text.isBlank() || assistantRequestInFlight || !GeminiConfig.isConfigured()) {
            return;
        }
        assistantInputField.clear();
        appendChatBubble("user", text);
        setAssistantBusy(true);

        List<ChatMessage> historySnapshot = List.copyOf(chatHistory);
        TreeItem<Path> selectedItem = fileTree.getSelectionModel().getSelectedItem();
        Path selectedFile = selectedItem != null ? selectedItem.getValue() : null;
        commandService.submit(
                () -> {
                    RepoSnapshot snapshot = model.snapshot();
                    StatusSnapshot status = commandExecutor.status();
                    String systemInstruction = ASSISTANT_PERSONA + "\n\n" + RepoContextSummary.build(snapshot, status)
                            + buildAttachedFileSection(selectedFile);
                    return geminiClient.generateReply(systemInstruction, historySnapshot, text);
                },
                reply -> {
                    chatHistory.add(ChatMessage.user(text));
                    chatHistory.add(ChatMessage.model(reply));
                    appendChatBubble("model", reply);
                    setAssistantBusy(false);
                },
                error -> {
                    appendChatBubble("model", "⚠ " + rootMessage(error));
                    setAssistantBusy(false);
                });
    }

    private static final long MAX_ATTACHED_FILE_BYTES = 200_000;
    private static final int MAX_ATTACHED_FILE_CHARS = 40_000;

    /** Off the FX thread (called from within the commandService.submit lambda above) — file reads are disk I/O. */
    private String buildAttachedFileSection(Path selectedFile) {
        if (selectedFile == null || !Files.isRegularFile(selectedFile)) {
            return "";
        }
        String relative = repoRoot.relativize(selectedFile).toString().replace('\\', '/');
        try {
            long size = Files.size(selectedFile);
            if (size > MAX_ATTACHED_FILE_BYTES) {
                return "\n\nThe user has " + relative + " selected in the sidebar, but it's too large ("
                        + size + " bytes) to include here — if asked about it, say so rather than guessing.\n";
            }
            String content = Files.readString(selectedFile);
            if (content.length() > MAX_ATTACHED_FILE_CHARS) {
                content = content.substring(0, MAX_ATTACHED_FILE_CHARS) + "\n... [truncated]";
            }
            return "\n\nThe user currently has this file selected in the sidebar — if their question seems to "
                    + "be about \"this file\"/\"this code\", ground your answer in it:\n\n"
                    + relative + ":\n```\n" + content + "\n```\n";
        } catch (IOException e) {
            // Likely a binary file JGit-style text tools can't decode -- just omit it rather than
            // failing the whole chat turn over an unreadable attachment.
            return "\n\nThe user has " + relative + " selected, but it couldn't be read as text "
                    + "(possibly a binary file) — if asked about it, say so rather than guessing.\n";
        }
    }

    private void updateAssistantAttachedFileLabel(TreeItem<Path> selectedItem) {
        Path selected = selectedItem != null ? selectedItem.getValue() : null;
        if (selected == null || !Files.isRegularFile(selected)) {
            assistantAttachedFileLabel.setText("No file selected — click a file in the sidebar to ask about its code.");
            return;
        }
        String relative = repoRoot.relativize(selected).toString().replace('\\', '/');
        assistantAttachedFileLabel.setText("📎 Attached: " + relative);
    }

    private void appendChatBubble(String role, String text) {
        Label bubble = new Label(text);
        bubble.setWrapText(true);
        bubble.setMaxWidth(520);
        bubble.getStyleClass().add("user".equals(role) ? "chat-bubble-user" : "chat-bubble-model");
        HBox row = new HBox(bubble);
        row.setAlignment("user".equals(role) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        assistantMessagesBox.getChildren().add(row);
        // The VBox's height hasn't updated within this call yet -- defer the scroll a pulse.
        Platform.runLater(() -> assistantScrollPane.setVvalue(1.0));
    }

    private void setAssistantBusy(boolean busy) {
        assistantRequestInFlight = busy;
        assistantInputField.setDisable(busy);
        assistantSendButton.setDisable(busy);
    }

    // ---- code relationship graph (CLAUDE.md 4.3 Tier 1) ----

    /** Manual "Analyze"/first-open entry point: (re)builds the scrubber range and jumps to live. */
    private void refreshCodeGraph() {
        codeGraphEverLoaded = true;
        codeGraphHistoryForScrubber = model.snapshot().commits();
        int liveIndex = codeGraphHistoryForScrubber.size();
        codeGraphTimeTravelSlider.setMin(0);
        codeGraphTimeTravelSlider.setMax(liveIndex);
        lastRenderedCodeGraphScrubberIndex = -1; // force a load even if the value doesn't actually change below
        if (codeGraphTimeTravelSlider.getValue() == liveIndex) {
            onCodeGraphScrubberChanged(liveIndex);
        } else {
            codeGraphTimeTravelSlider.setValue(liveIndex);
        }
    }

    private boolean isViewingLiveCodeGraph() {
        return Math.round(codeGraphTimeTravelSlider.getValue()) >= codeGraphHistoryForScrubber.size();
    }

    /**
     * Slider position {@code codeGraphHistoryForScrubber.size()} means "live" (parses straight off
     * disk); any earlier position re-analyzes a historical commit's blobs via
     * {@link HistoricalJavaSourceReader} — no checkout, so scrubbing never touches the live
     * working directory (CLAUDE.md 4.3). Tier 2 symbol resolution needs real files on disk to
     * resolve against (see {@link JavaDependencyAnalyzer#analyzeWithSymbolResolution}), so it's
     * only offered while viewing live — the checkbox is disabled otherwise and historical points
     * always fall back to Tier 1.
     */
    private void onCodeGraphScrubberChanged(int index) {
        if (index == lastRenderedCodeGraphScrubberIndex) {
            return;
        }
        lastRenderedCodeGraphScrubberIndex = index;
        updateCodeGraphScrubberLabel(index);
        codeGraphDetailPanel.setVisible(false);
        codeGraphDetailPanel.setManaged(false);
        codeGraphStatusLabel.setText("Parsing .java files...");

        boolean isLive = index >= codeGraphHistoryForScrubber.size();
        codeGraphTier2Check.setDisable(!isLive);

        if (isLive) {
            boolean useTier2 = codeGraphTier2Check.isSelected();
            commandService.submit(
                    () -> useTier2 ? JavaDependencyAnalyzer.analyzeWithSymbolResolution(repoRoot) : JavaDependencyAnalyzer.analyze(repoRoot),
                    this::onCodeGraphLoaded,
                    error -> codeGraphStatusLabel.setText("✗ analysis failed: " + rootMessage(error)));
            return;
        }
        ObjectId commitId = codeGraphHistoryForScrubber.get(index).id();
        commandService.submit(
                () -> JavaDependencyAnalyzer.analyzeSources(HistoricalJavaSourceReader.read(model.getRepository(), commitId)),
                this::onCodeGraphLoaded,
                error -> codeGraphStatusLabel.setText("✗ analysis failed: " + rootMessage(error)));
    }

    private void updateCodeGraphScrubberLabel(int index) {
        if (index >= codeGraphHistoryForScrubber.size()) {
            codeGraphTimeTravelLabel.setText("Live (working tree)");
            return;
        }
        CommitNode commit = codeGraphHistoryForScrubber.get(index);
        String when = SCRUBBER_TIME.format(Instant.ofEpochSecond(commit.commitEpochSeconds()).atZone(ZoneId.systemDefault()));
        codeGraphTimeTravelLabel.setText(when + " — " + commit.shortMessage());
    }

    /** New commits extend the scrubber's range; only bumps the position (not a re-analysis) when already viewing live. */
    private void refreshCodeGraphScrubberRangeAfterCommand() {
        if (!codeGraphEverLoaded) {
            return;
        }
        boolean wasAtLive = isViewingLiveCodeGraph();
        codeGraphHistoryForScrubber = model.snapshot().commits();
        int liveIndex = codeGraphHistoryForScrubber.size();
        codeGraphTimeTravelSlider.setMax(liveIndex);
        if (wasAtLive) {
            if (codeGraphTimeTravelSlider.getValue() == liveIndex) {
                lastRenderedCodeGraphScrubberIndex = -1;
                onCodeGraphScrubberChanged(liveIndex);
            } else {
                codeGraphTimeTravelSlider.setValue(liveIndex);
            }
        }
    }

    private void onCodeGraphLoaded(JavaDependencyGraph graph) {
        currentCodeGraph = graph;
        String status = graph.filePaths().size() + " file(s), " + graph.edges().size() + " connection(s)";
        if (!graph.filesWithParseErrors().isEmpty()) {
            status += " — " + graph.filesWithParseErrors().size() + " couldn't be fully parsed";
        }
        codeGraphStatusLabel.setText(status);

        // A canvas sized to just the viewport crams every file into the same tiny box regardless
        // of how many there are — give each file real breathing room (scrollable, per CodeGraphView's
        // ScrollPane host) instead of always fitting to whatever's currently visible.
        double viewportWidth = codeGraphHost.getWidth() > 0 ? codeGraphHost.getWidth() : 700;
        double viewportHeight = codeGraphHost.getHeight() > 0 ? codeGraphHost.getHeight() : 500;
        int fileCount = Math.max(graph.filePaths().size(), 1);
        double side = Math.sqrt(fileCount * 34000.0);
        double width = Math.max(viewportWidth, Math.min(side, 4600));
        double height = Math.max(viewportHeight, Math.min(side * 0.72, 3400));
        codeGraphView.render(graph, width, height);
    }

    private void showCodeGraphFileDetail(String path) {
        if (path == null || currentCodeGraph == null) {
            hideCodeGraphDetailIfEmpty();
            return;
        }
        codeGraphDetailPanel.setVisible(true);
        codeGraphDetailPanel.setManaged(true);
        codeGraphDetailHeadingLabel.setText("Selected File");
        codeGraphSelectedPathLabel.setText(path);
        codeGraphDependsOnHeadingLabel.setText("DEPENDS ON");
        codeGraphUsedByHeadingLabel.setText("USED BY");

        codeGraphDependsOnBox.getChildren().clear();
        codeGraphUsedByBox.getChildren().clear();
        for (DependencyEdge edge : currentCodeGraph.edges()) {
            if (edge.fromPath().equals(path)) {
                addDetailRow(codeGraphDependsOnBox, edge.toPath(), usageSummary(edge));
            }
            if (edge.toPath().equals(path)) {
                addDetailRow(codeGraphUsedByBox, edge.fromPath(), usageSummary(edge));
            }
        }
        if (codeGraphDependsOnBox.getChildren().isEmpty()) {
            addEmptyRow(codeGraphDependsOnBox, "Nothing in this codebase");
        }
        if (codeGraphUsedByBox.getChildren().isEmpty()) {
            addEmptyRow(codeGraphUsedByBox, "Nothing in this codebase");
        }
    }

    private void showCodeGraphEdgeDetail(DependencyEdge edge) {
        if (edge == null) {
            hideCodeGraphDetailIfEmpty();
            return;
        }
        codeGraphDetailPanel.setVisible(true);
        codeGraphDetailPanel.setManaged(true);
        codeGraphDetailHeadingLabel.setText("Selected Connection");
        codeGraphSelectedPathLabel.setText(edge.fromPath() + "  →  " + edge.toPath());
        codeGraphDependsOnHeadingLabel.setText("TYPE(S) REFERENCED");
        codeGraphUsedByHeadingLabel.setText("METHOD CALL(S) DETECTED");

        codeGraphDependsOnBox.getChildren().clear();
        codeGraphUsedByBox.getChildren().clear();
        if (edge.referencedTypeNames().isEmpty()) {
            addEmptyRow(codeGraphDependsOnBox, "None — only a method call was detected");
        } else {
            edge.referencedTypeNames().forEach(type -> addSimpleRow(codeGraphDependsOnBox, type));
        }
        if (edge.referencedMethodNames().isEmpty()) {
            addEmptyRow(codeGraphUsedByBox, "None detected — best-effort only");
        } else {
            edge.referencedMethodNames().forEach(method -> addSimpleRow(codeGraphUsedByBox, method));
        }
    }

    /** A click on empty canvas fires both selection callbacks with null — only hide once both agree nothing is focused. */
    private void hideCodeGraphDetailIfEmpty() {
        codeGraphDetailPanel.setVisible(false);
        codeGraphDetailPanel.setManaged(false);
    }

    /** One "X depends on/is used by Y (because of Z)" row: the related file in bright text, the reason underneath in muted text. */
    private static void addDetailRow(VBox box, String primaryText, String secondaryText) {
        Label primary = new Label(primaryText);
        primary.setWrapText(true);
        primary.getStyleClass().add("detail-item-primary");
        Label secondary = new Label(secondaryText);
        secondary.setWrapText(true);
        secondary.getStyleClass().add("detail-item-secondary");
        box.getChildren().add(new VBox(2, primary, secondary));
    }

    private static void addSimpleRow(VBox box, String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("detail-item-primary");
        box.getChildren().add(label);
    }

    private static void addEmptyRow(VBox box, String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("detail-empty");
        box.getChildren().add(label);
    }

    private static String usageSummary(DependencyEdge edge) {
        List<String> parts = new ArrayList<>(edge.referencedTypeNames());
        edge.referencedMethodNames().forEach(m -> parts.add(m));
        return String.join(", ", parts);
    }

    // ---- sidebar / terminal chrome ----

    /** Opens this sandbox session's actual folder in the OS file explorer — the "no built-in editor" flow needs the user to know where that is, since it's an app-managed temp copy, not wherever they originally pointed at (CLAUDE.md 4.3). */
    private void openSandboxFolder() {
        try {
            java.awt.Desktop.getDesktop().open(repoRoot.toFile());
        } catch (Exception e) {
            ErrorDialogs.show("Couldn't open the sandbox folder", e);
        }
    }

    private static final ButtonType SAVE_BUTTON = new ButtonType("Save As...");
    private static final ButtonType DISCARD_BUTTON = new ButtonType("Discard");

    /**
     * Every session backed by disposable temp directories (CLAUDE.md's true-sandbox guarantee —
     * see {@code RepositorySessionFactory}) gets torn down on the way out. Free-play sessions
     * ({@link RepoStateModel#offersSaveOnDiscard()}) get asked first — Save As copies this
     * session's actual files (commits and all) somewhere permanent before deleting the temp
     * original; Discard just deletes it; Cancel stays put. Campaign levels and Collaboration Demo
     * clones aren't offered a save (nothing there is meant to be kept as a standalone project) —
     * their temp directories are just silently cleaned up.
     */
    private void leaveSandbox(Runnable navigateAway) {
        List<Path> disposablePaths = model.disposablePaths();
        if (disposablePaths.isEmpty()) {
            navigateAway.run();
            return;
        }
        if (!model.offersSaveOnDiscard()) {
            commandService.submit(() -> {
                        model.close(); // release JGit's file handles first -- Windows won't delete an open file
                        TempDirCleanup.deleteAll(disposablePaths);
                        return null;
                    },
                    ignored -> navigateAway.run(),
                    error -> navigateAway.run()); // best-effort cleanup; still leave either way
            return;
        }

        Alert prompt = new Alert(AlertType.CONFIRMATION,
                "Keep this sandbox's commits and files permanently, or discard them?",
                SAVE_BUTTON, DISCARD_BUTTON, ButtonType.CANCEL);
        prompt.setHeaderText("Save this sandbox before leaving?");
        Optional<ButtonType> choice = prompt.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
            return;
        }
        if (choice.get() == DISCARD_BUTTON) {
            commandService.submit(() -> {
                        model.close(); // release JGit's file handles first -- Windows won't delete an open file
                        TempDirCleanup.deleteAll(disposablePaths);
                        return null;
                    },
                    ignored -> navigateAway.run(),
                    error -> navigateAway.run());
            return;
        }

        Path destination = chooseSaveDestination();
        if (destination == null) {
            return; // folder picker cancelled, or destination rejected -- stay put
        }
        commandService.submit(() -> {
                    // Copying works fine with the repository still open (Windows only blocks
                    // deletion of an open file, not reading it) -- close it only once we're done
                    // reading from it, right before deleting the temp original.
                    FileTreeCopy.copyRecursively(repoRoot, destination);
                    // The copy still has "origin" pointing at a local mirror that's about to be
                    // deleted along with the rest of the temp sandbox -- strip it so the saved
                    // copy doesn't carry a dangling remote.
                    try (var savedGit = org.eclipse.jgit.api.Git.open(destination.toFile())) {
                        if (savedGit.getRepository().getConfig().getString("remote", "origin", "url") != null) {
                            savedGit.remoteRemove().setRemoteName("origin").call();
                        }
                    }
                    model.close();
                    TempDirCleanup.deleteAll(disposablePaths);
                    return null;
                },
                ignored -> navigateAway.run(),
                // Save failed -- don't delete the temp original, and don't navigate away, so nothing is lost.
                error -> ErrorDialogs.show("Couldn't save sandbox", error));
    }

    /** Null if the user cancelled the picker, or declined to save into a non-empty folder. */
    private Path chooseSaveDestination() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose where to save this sandbox");
        Window window = homeButton.getScene() != null ? homeButton.getScene().getWindow() : null;
        File selected = chooser.showDialog(window);
        if (selected == null) {
            return null;
        }
        Path destination = selected.toPath();
        try (var listing = Files.list(destination)) {
            if (listing.findAny().isPresent()) {
                Alert warn = new Alert(AlertType.CONFIRMATION,
                        destination + " isn't empty. Save into it anyway?", ButtonType.YES, ButtonType.NO);
                Optional<ButtonType> choice = warn.showAndWait();
                if (choice.isEmpty() || choice.get() != ButtonType.YES) {
                    return null;
                }
            }
        } catch (IOException e) {
            ErrorDialogs.show("Couldn't check destination folder", e);
            return null;
        }
        return destination;
    }

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
        if (verb.equals("reflog")) {
            logLine(trimmed);
            commandService.submit(commandExecutor::reflog,
                    lines -> lines.forEach(line -> logLine("  " + line)),
                    error -> logLine("  ✗ " + rootMessage(error)));
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

    private record UndoPreview(boolean mergePending, List<String> reflogLines) {
    }

    /**
     * Previews what's about to happen before committing to a destructive hard reset. A pending
     * conflicted merge never shows up in the HEAD reflog (it doesn't move HEAD), so that case is
     * previewed/handled separately — matching {@link CommandExecutor#undoLastCommand()}'s own
     * merge-pending special case.
     */
    private void handleUndoLastCommand() {
        commandService.submit(() -> {
                    List<ObjectId> mergeHeads = model.getRepository().readMergeHeads();
                    boolean mergePending = mergeHeads != null && !mergeHeads.isEmpty();
                    return new UndoPreview(mergePending, mergePending ? List.of() : commandExecutor.reflog());
                },
                preview -> {
                    String message;
                    if (preview.mergePending()) {
                        message = "This cancels the pending conflicted merge (same as \"merge --abort\") "
                                + "and restores the working tree to before the merge attempt.";
                    } else if (preview.reflogLines().isEmpty()) {
                        logLine("Undo last command");
                        logLine("  ✗ nothing to undo yet — the reflog is empty");
                        return;
                    } else {
                        message = "This hard-resets back to just before:\n\n" + preview.reflogLines().get(0)
                                + "\n\nAny uncommitted staged/tracked changes will be lost "
                                + "(untracked files are left alone).";
                    }
                    Alert confirm = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
                    confirm.setHeaderText("Undo last command?");
                    Optional<ButtonType> choice = confirm.showAndWait();
                    if (choice.isPresent() && choice.get() == ButtonType.YES) {
                        run("undo last command (reflog-backed reset)", commandExecutor::undoLastCommand);
                    }
                },
                error -> logLine("  ✗ couldn't check undo preview: " + rootMessage(error)));
    }

    // ---- shared dispatch ----

    private void run(String description, Callable<GraphDiff> work) {
        logLine(description);
        setBusy(true);
        commandService.submit(work, diff -> {
            onCommandSucceeded(diff);
            // A fetch/pull just updated the remote-tracking refs the poller compares against —
            // re-check right away instead of waiting out the rest of the polling interval.
            String verb = description.toLowerCase();
            if (verb.contains("fetch") || verb.contains("pull")) {
                commandService.submit(() -> remotePoller.checkNow(), ignored -> { }, ignored -> { });
            }
        }, this::onError);
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
        checkLevelGoal();
        refreshCodeGraphScrubberRangeAfterCommand();
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
        checkLevelGoal();
        refreshCodeGraphScrubberRangeAfterCommand();
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

    // ---- remote update notice (CLAUDE.md 4.3) ----

    private void handleCheckRemote() {
        logLine("Checking origin for updates...");
        commandService.submit(() -> remotePoller.checkNow(),
                changed -> {
                    if (changed == null) {
                        logLine("  ✗ no \"origin\" remote configured, or couldn't reach it");
                    } else if (changed.isEmpty()) {
                        logLine("  ✓ up to date with origin");
                    } else {
                        logLine("  ⬇ " + String.join(", ", changed) + " on origin has new commits");
                    }
                },
                error -> logLine("  ✗ couldn't check origin: " + rootMessage(error)));
    }

    /** Called from {@link RemoteRefPoller}'s own background thread (periodic tick or manual check) — hop to FX before touching UI. */
    private void onRemoteRefsChanged(Set<String> changedBranches) {
        Platform.runLater(() -> {
            boolean hasNews = !changedBranches.isEmpty();
            if (hasNews) {
                List<String> withOrigin = changedBranches.stream().map(name -> "origin/" + name).sorted().toList();
                remoteNoticeLabel.setText(String.join(", ", withOrigin) + " " + (withOrigin.size() == 1 ? "has" : "have") + " new commits");
            }
            remoteNoticeLabel.setVisible(hasNews);
            remoteNoticeLabel.setManaged(hasNews);
            fetchNowButton.setVisible(hasNews);
            fetchNowButton.setManaged(hasNews);
        });
    }

    private void refreshDirtyCount() {
        commandService.submit(commandExecutor::status,
                status -> {
                    latestStatus = status;
                    dirtyLabel.setText("Dirty files: " + status.dirtyFileCount());
                    fileTree.refresh();
                    // Conflicts are a pending state, not a failure (CLAUDE.md 4.3) — flag it
                    // once on the transition into conflict rather than on every refresh.
                    boolean conflicted = !status.conflicting().isEmpty();
                    if (conflicted && !conflictWarningShown) {
                        conflictWarningShown = true;
                        logLine("  ⚠ merge conflict pending in " + String.join(", ", status.conflicting())
                                + " — resolve it by hand or run \"merge --abort\"");
                    } else if (!conflicted) {
                        conflictWarningShown = false;
                    }
                    updateConflictedFilesListItems(status.conflicting());
                    updatePendingMergeVisual();
                },
                error -> dirtyLabel.setText("Dirty files: -"));
    }

    // ---- merge conflicts (CLAUDE.md 4.3) ----

    /** A pending (conflicted) merge gets a distinct dashed, pulsing placeholder on the commit graph rather than an error. */
    private void updatePendingMergeVisual() {
        commandService.submit(() -> {
            List<ObjectId> mergeHeads = model.getRepository().readMergeHeads();
            return mergeHeads != null && !mergeHeads.isEmpty() ? mergeHeads.get(0) : null;
        }, theirsId -> {
            if (theirsId == null) {
                commitGraphView.clearPendingMerge();
                return;
            }
            RepoSnapshot snapshot = model.snapshot();
            CommitNode ours = snapshot.commits().stream()
                    .filter(c -> c.id().equals(snapshot.headCommitId())).findFirst().orElse(null);
            CommitNode theirs = snapshot.commits().stream()
                    .filter(c -> c.id().equals(theirsId)).findFirst().orElse(null);
            if (ours != null && theirs != null) {
                commitGraphView.showPendingMerge(ours, theirs);
            } else {
                commitGraphView.clearPendingMerge();
            }
        }, error -> commitGraphView.clearPendingMerge());
    }

    private void updateConflictedFilesListItems(Set<String> conflicting) {
        List<String> sorted = new ArrayList<>(conflicting);
        Collections.sort(sorted);
        String previousSelection = conflictedFilesList.getSelectionModel().getSelectedItem();
        conflictedFilesList.getItems().setAll(sorted);
        if (previousSelection != null && sorted.contains(previousSelection)) {
            conflictedFilesList.getSelectionModel().select(previousSelection);
        } else {
            conflictDiffView.clear();
        }
    }

    private void refreshConflictedFilesList() {
        if (latestStatus != null) {
            updateConflictedFilesListItems(latestStatus.conflicting());
        } else {
            refreshDirtyCount();
        }
    }

    private void loadConflictDiff(String filePath) {
        if (filePath == null) {
            conflictDiffView.clear();
            return;
        }
        commandService.submit(() -> ConflictDiffReader.read(model.getRepository(), filePath),
                conflictDiffView::render,
                error -> conflictDiffView.showMessage("Couldn't show a text diff for " + filePath
                        + " — it may be a binary file, or a delete/rename conflict rather than two sides "
                        + "editing the same text. Resolve it by editing the file directly (or deciding "
                        + "whether to keep or delete it), then stage it."));
    }

    private void resolveSelectedConflict(boolean keepOurs) {
        String filePath = conflictedFilesList.getSelectionModel().getSelectedItem();
        if (filePath == null) {
            return;
        }
        commandService.submit(() -> ConflictDiffReader.read(model.getRepository(), filePath),
                diff -> {
                    try {
                        Files.writeString(repoRoot.resolve(filePath), keepOurs ? diff.oursContent() : diff.theirsContent());
                    } catch (IOException e) {
                        ErrorDialogs.show("Couldn't resolve conflict", e);
                        return;
                    }
                    String label = keepOurs ? "Keep Mine" : "Keep Theirs";
                    run("git add " + filePath + "  (resolved via \"" + label + "\")", commandExecutor::stageAll);
                },
                error -> ErrorDialogs.show("Couldn't resolve conflict", error));
    }

    private void setBusy(boolean busy) {
        commandAccordion.setDisable(busy);
        terminalInputField.setDisable(busy);
    }
}
