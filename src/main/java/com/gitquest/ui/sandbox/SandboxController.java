package com.gitquest.ui.sandbox;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.campaign.CampaignProgress;
import com.gitquest.core.campaign.LevelDefinition;
import com.gitquest.core.codebase.CodebaseAnalyzer;
import com.gitquest.core.codebase.FileEntry;
import com.gitquest.core.codebase.HistoricalTreeReader;
import com.gitquest.core.codebase.WorkingTreeScanner;
import com.gitquest.core.codegraph.DependencyEdge;
import com.gitquest.core.codegraph.HistoricalJavaSourceReader;
import com.gitquest.core.codegraph.JavaDependencyAnalyzer;
import com.gitquest.core.codegraph.JavaDependencyGraph;
import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.command.StatusSnapshot;
import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.CommandService;
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
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
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
    private static final DateTimeFormatter SCRUBBER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
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
    private TabPane mainTabPane;
    @FXML
    private Tab codebaseTab;
    @FXML
    private ChoiceBox<String> treemapOverlayChoice;
    @FXML
    private Button refreshAnalysisButton;
    @FXML
    private Slider timeTravelSlider;
    @FXML
    private Label timeTravelLabel;
    @FXML
    private ScrollPane treemapScrollPane;
    @FXML
    private TreemapView treemapView;
    @FXML
    private VBox fileDetailPanel;
    @FXML
    private Label fileDetailPathLabel;
    @FXML
    private Label fileDetailStatsLabel;
    @FXML
    private Label fileDetailLastCommitLabel;
    @FXML
    private Tab codeGraphTab;
    @FXML
    private Button analyzeCodeGraphButton;
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
    private VBox campaignBanner;
    @FXML
    private Label levelTitleLabel;
    @FXML
    private Label objectiveLabel;
    @FXML
    private Label whyItMattersLabel;
    @FXML
    private Button hintButton;
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
    private LevelDefinition activeLevel;
    private int hintTierUsed;
    private boolean levelCompletedThisSession;
    private boolean conflictWarningShown;

    // ---- codebase visualizer state (CLAUDE.md 4.3) ----
    private Map<String, CodebaseAnalyzer.FileStats> fileStatsByPath = Map.of();
    private FileEntry liveWorkingTreeRoot;
    private FileEntry currentTreemapRoot;
    private List<CommitNode> historyForScrubber = List.of();
    private int lastRenderedScrubberIndex = -1;
    private boolean codebaseAnalysisEverLoaded;

    // ---- code relationship graph state (CLAUDE.md 4.3 Tier 1) ----
    private JavaDependencyGraph currentCodeGraph;
    private boolean codeGraphEverLoaded;
    private List<CommitNode> codeGraphHistoryForScrubber = List.of();
    private int lastRenderedCodeGraphScrubberIndex = -1;

    @FXML
    private void initialize() {
        stageButton.setOnAction(e -> run("git add .", () -> commandExecutor.stageAll()));
        commitButton.setOnAction(e -> handleCommit());
        refreshButton.setOnAction(e -> handleRefresh());
        createBranchButton.setOnAction(e -> handleCreateBranch());
        checkoutButton.setOnAction(e -> handleCheckout());
        mergeButton.setOnAction(e -> handleMerge());

        collapseSidebarButton.setOnAction(e -> toggleSidebar());
        terminalInputField.setOnAction(e -> handleTerminalCommand());

        treemapOverlayChoice.getItems().setAll("Size", "Churn", "Recency");
        treemapOverlayChoice.setValue("Size");
        treemapOverlayChoice.getSelectionModel().selectedItemProperty()
                .addListener((obs, oldVal, newVal) -> renderTreemap());
        treemapView.setOnFileSelected(this::showFileDetail);
        refreshAnalysisButton.setOnAction(e -> refreshCodebaseAnalysis());
        timeTravelSlider.valueProperty().addListener((obs, oldVal, newVal) -> onScrubberChanged(newVal.intValue()));
        treemapScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> renderTreemap());
        analyzeCodeGraphButton.setOnAction(e -> refreshCodeGraph());
        codeGraphView.setOnNodeSelected(this::showCodeGraphFileDetail);
        codeGraphView.setOnEdgeSelected(this::showCodeGraphEdgeDetail);
        codeGraphTimeTravelSlider.valueProperty().addListener((obs, oldVal, newVal) -> onCodeGraphScrubberChanged(newVal.intValue()));
        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab == codebaseTab && !codebaseAnalysisEverLoaded) {
                refreshCodebaseAnalysis();
            }
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
        });
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
        // Keep file sizes live without re-running the (expensive, per-file git log) churn
        // analysis on every edit — that stays a manual "Refresh Analysis" action.
        if (codebaseAnalysisEverLoaded) {
            commandService.submit(() -> WorkingTreeScanner.scan(repoRoot),
                    workingTree -> {
                        liveWorkingTreeRoot = workingTree;
                        if (isViewingLive()) {
                            currentTreemapRoot = workingTree;
                            renderTreemap();
                        }
                    },
                    error -> { /* transient scan failure; the next file-watcher tick will retry */ });
        }
    }

    // ---- campaign mode ----

    /** Enters this Sandbox screen scoped to a Campaign level instead of free play — see class javadoc. */
    public void setCampaignLevel(RepoStateModel model, LevelDefinition level, Navigator navigator) {
        this.activeLevel = level;
        setModel(model);

        campaignBanner.setVisible(true);
        campaignBanner.setManaged(true);
        levelTitleLabel.setText(level.title());
        objectiveLabel.setText(level.objective());
        whyItMattersLabel.setText("Why it matters: " + level.whyItMatters());
        hintButton.setOnAction(e -> handleHint());
        backToSkillTreeButton.setOnAction(e -> navigator.showCampaign());

        // Campaign levels are terminal-only — no button palette. Typing the
        // real commands is the point of a *guided* campaign.
        commandsHeaderLabel.setVisible(false);
        commandsHeaderLabel.setManaged(false);
        commandAccordion.setVisible(false);
        commandAccordion.setManaged(false);
        terminalInputField.requestFocus();
    }

    private void handleHint() {
        if (hintTierUsed == 0) {
            hintTierUsed = 1;
            new Alert(AlertType.INFORMATION, activeLevel.freeHint(), ButtonType.OK).showAndWait();
            return;
        }
        Alert confirm = new Alert(AlertType.CONFIRMATION,
                "This will reveal the exact commands. Continue?", ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Show solution?");
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isPresent() && choice.get() == ButtonType.YES) {
            hintTierUsed = 2;
            new Alert(AlertType.INFORMATION, activeLevel.solutionHint(), ButtonType.OK).showAndWait();
        }
    }

    /** Checked off the FX thread — a goal may need real JGit/disk reads (see GitignoreExcludesGoal). */
    private void checkLevelGoal() {
        if (activeLevel == null || levelCompletedThisSession) {
            return;
        }
        LevelDefinition level = activeLevel;
        commandService.submit(() -> level.goal().isSatisfied(model),
                satisfied -> {
                    if (satisfied) {
                        onLevelGoalSatisfied();
                    }
                },
                error -> { /* transient check failure; next command will re-check */ });
    }

    private void onLevelGoalSatisfied() {
        levelCompletedThisSession = true;
        CampaignProgress progress = progressStore.load();
        boolean firstTime = !progress.isLevelCompleted(activeLevel.id());
        progress.recordCompletion(activeLevel.id(), hintTierUsed);
        progressStore.save(progress);

        Alert done = new Alert(AlertType.INFORMATION,
                firstTime ? "Nice work!" : "Solved again — this level was already completed earlier.",
                ButtonType.OK);
        done.setHeaderText("🎉 " + activeLevel.title() + " complete");
        done.showAndWait();
    }

    // ---- codebase visualizer (CLAUDE.md 4.3) ----

    private record CodebaseSnapshot(FileEntry workingTree, Map<String, CodebaseAnalyzer.FileStats> stats) {
    }

    private void refreshCodebaseAnalysis() {
        codebaseAnalysisEverLoaded = true;
        logLine("Analyzing codebase (churn/recency)...");
        commandService.submit(this::computeCodebaseSnapshot, this::onCodebaseAnalysisLoaded,
                error -> logLine("  ✗ codebase analysis failed: " + rootMessage(error)));
    }

    /** Off the FX thread: a full working-tree scan plus one path-filtered {@code git log} per file. */
    private CodebaseSnapshot computeCodebaseSnapshot() throws Exception {
        FileEntry workingTree = WorkingTreeScanner.scan(repoRoot);
        Map<String, CodebaseAnalyzer.FileStats> stats = new HashMap<>();
        for (String path : collectFilePaths(workingTree)) {
            stats.put(path, CodebaseAnalyzer.analyze(model.getGit(), path));
        }
        return new CodebaseSnapshot(workingTree, stats);
    }

    private static List<String> collectFilePaths(FileEntry entry) {
        List<String> paths = new ArrayList<>();
        collectFilePaths(entry, paths);
        return paths;
    }

    private static void collectFilePaths(FileEntry entry, List<String> out) {
        if (entry.directory()) {
            for (FileEntry child : entry.children()) {
                collectFilePaths(child, out);
            }
        } else {
            out.add(entry.relativePath());
        }
    }

    private void onCodebaseAnalysisLoaded(CodebaseSnapshot snapshot) {
        liveWorkingTreeRoot = snapshot.workingTree();
        fileStatsByPath = snapshot.stats();
        logLine("  ✓ codebase analysis done (" + fileStatsByPath.size() + " file(s))");
        setupTimeTravelSlider();
    }

    /** Slider position {@code historyForScrubber.size()} means "live" (the working tree, dirty edits included). */
    private void setupTimeTravelSlider() {
        historyForScrubber = model.snapshot().commits();
        int liveIndex = historyForScrubber.size();
        timeTravelSlider.setMin(0);
        timeTravelSlider.setMax(liveIndex);
        lastRenderedScrubberIndex = -1; // force a render even if the value doesn't actually change below
        if (timeTravelSlider.getValue() == liveIndex) {
            onScrubberChanged(liveIndex);
        } else {
            timeTravelSlider.setValue(liveIndex);
        }
    }

    private boolean isViewingLive() {
        return Math.round(timeTravelSlider.getValue()) >= historyForScrubber.size();
    }

    /** Reads the historical tree at a scrubbed commit via {@link HistoricalTreeReader} — no checkout, live working tree untouched. */
    private void onScrubberChanged(int index) {
        if (index == lastRenderedScrubberIndex || liveWorkingTreeRoot == null) {
            return;
        }
        lastRenderedScrubberIndex = index;
        updateScrubberLabel(index);
        if (index >= historyForScrubber.size()) {
            currentTreemapRoot = liveWorkingTreeRoot;
            renderTreemap();
            return;
        }
        ObjectId commitId = historyForScrubber.get(index).id();
        commandService.submit(() -> HistoricalTreeReader.read(model.getRepository(), commitId),
                tree -> {
                    currentTreemapRoot = tree;
                    renderTreemap();
                },
                error -> logLine("  ✗ couldn't read history at that point: " + rootMessage(error)));
    }

    private void updateScrubberLabel(int index) {
        if (index >= historyForScrubber.size()) {
            timeTravelLabel.setText("Live (working tree)");
            return;
        }
        CommitNode commit = historyForScrubber.get(index);
        String when = SCRUBBER_TIME.format(Instant.ofEpochSecond(commit.commitEpochSeconds()).atZone(ZoneId.systemDefault()));
        timeTravelLabel.setText(when + " — " + commit.shortMessage());
    }

    /** Re-lays out {@link #currentTreemapRoot} to fill the scroll pane's viewport, morphing existing cells (CLAUDE.md Section 6). */
    private void renderTreemap() {
        if (currentTreemapRoot == null) {
            return;
        }
        treemapView.setColorFunction(currentColorFunction());
        var viewport = treemapScrollPane.getViewportBounds();
        double width = Math.max(viewport.getWidth(), 200);
        double height = Math.max(viewport.getHeight(), 200);
        treemapView.render(currentTreemapRoot, width, height);
    }

    private java.util.function.Function<FileEntry, javafx.scene.paint.Color> currentColorFunction() {
        return switch (treemapOverlayChoice.getValue()) {
            case "Churn" -> TreemapColorModes.byChurn(fileStatsByPath);
            case "Recency" -> TreemapColorModes.byRecency(fileStatsByPath);
            default -> TreemapColorModes.byExtension();
        };
    }

    private void showFileDetail(FileEntry entry) {
        fileDetailPanel.setVisible(true);
        fileDetailPanel.setManaged(true);
        fileDetailPathLabel.setText(entry.relativePath());

        CodebaseAnalyzer.FileStats stats = fileStatsByPath.get(entry.relativePath());
        if (stats == null || stats.commitCount() == 0) {
            fileDetailStatsLabel.setText(humanSize(entry.size()) + " — not yet committed");
            fileDetailLastCommitLabel.setText("");
            return;
        }
        fileDetailStatsLabel.setText(humanSize(entry.size()) + " — " + stats.commitCount() + " commit(s) — "
                + stats.contributors().size() + " contributor(s): " + String.join(", ", stats.contributors()));
        String lastWhen = SCRUBBER_TIME.format(
                Instant.ofEpochSecond(stats.lastCommitEpochSeconds()).atZone(ZoneId.systemDefault()));
        fileDetailLastCommitLabel.setText("Last: \"" + stats.lastCommitMessage() + "\" (" + lastWhen + ")");
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        return String.format("%.1f MB", kb / 1024.0);
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
     * working directory (CLAUDE.md 4.3).
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

        if (index >= codeGraphHistoryForScrubber.size()) {
            commandService.submit(() -> JavaDependencyAnalyzer.analyze(repoRoot),
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
        checkLevelGoal();
        refreshScrubberRangeAfterCommand();
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
        refreshScrubberRangeAfterCommand();
        refreshCodeGraphScrubberRangeAfterCommand();
    }

    /** New commits extend the time-travel scrubber's range; only bumps the position (not a re-analysis) when already viewing live. */
    private void refreshScrubberRangeAfterCommand() {
        if (!codebaseAnalysisEverLoaded) {
            return;
        }
        boolean wasAtLive = isViewingLive();
        historyForScrubber = model.snapshot().commits();
        int liveIndex = historyForScrubber.size();
        timeTravelSlider.setMax(liveIndex);
        if (wasAtLive) {
            if (timeTravelSlider.getValue() == liveIndex) {
                lastRenderedScrubberIndex = -1;
                onScrubberChanged(liveIndex);
            } else {
                timeTravelSlider.setValue(liveIndex);
            }
        }
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
                },
                error -> dirtyLabel.setText("Dirty files: -"));
    }

    private void setBusy(boolean busy) {
        commandAccordion.setDisable(busy);
        terminalInputField.setDisable(busy);
    }
}
