package com.gitquest.ui.sandbox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.command.CommandExecutor;
import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.GraphDiff;
import com.gitquest.core.model.RepoSnapshot;
import com.gitquest.core.model.RepoStateModel;
import com.gitquest.core.service.CommandService;
import com.gitquest.ui.common.ErrorDialogs;

import javafx.scene.control.TextInputDialog;

/**
 * Wires {@link SandboxView}'s command palette to {@link CommandExecutor} via
 * {@link CommandService}, animating the resulting {@link GraphDiff} on
 * success. Every read of "current" repo state uses {@link RepoStateModel#snapshot()}
 * rather than the observable lists, since a snapshot is guaranteed to already
 * reflect the just-completed command (it's populated synchronously on the
 * background thread before the command's result reaches this, FX-thread, code).
 */
public final class SandboxController {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final RepoStateModel model;
    private final SandboxView view;
    private final CommandExecutor commandExecutor;
    private final CommandService commandService = new CommandService();

    public SandboxController(RepoStateModel model, SandboxView view) {
        this.model = model;
        this.view = view;
        this.commandExecutor = new CommandExecutor(model);

        RepoSnapshot initial = model.snapshot();
        view.getCommitGraphView().renderInitial(initial.commits());
        view.getCommitGraphView().syncRefsAndHead(initial.commits(), initial.headRefName(), initial.headCommitId());
        refreshBranchChoices(initial);
        refreshHeadAndBranchLabels(initial);
        refreshDirtyCount();

        wireButtons();
    }

    private void wireButtons() {
        view.getCommandPalette().getStageButton().setOnAction(e -> run("git add .", commandExecutor::stageAll));
        view.getCommandPalette().getCommitButton().setOnAction(e -> handleCommit());
        view.getCommandPalette().getRefreshButton().setOnAction(e -> handleRefresh());
        view.getCommandPalette().getCreateBranchButton().setOnAction(e -> handleCreateBranch());
        view.getCommandPalette().getCheckoutButton().setOnAction(e -> handleCheckout());
        view.getCommandPalette().getMergeButton().setOnAction(e -> handleMerge());
    }

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
        String description = "refresh (re-read repository from disk)";
        logLine(description);
        setBusy(true);
        commandService.submit(() -> {
            model.refresh();
            return model.snapshot();
        }, this::onManualRefresh, error -> onError(description, error));
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
        String target = view.getCommandPalette().getCheckoutTargetChoice().getValue();
        if (target != null) {
            run("git checkout " + target, () -> commandExecutor.checkout(target));
        }
    }

    private void handleMerge() {
        String source = view.getCommandPalette().getMergeSourceChoice().getValue();
        if (source == null) {
            return;
        }
        boolean noFastForward = view.getCommandPalette().getNoFastForwardCheck().isSelected();
        String description = "git merge " + source + (noFastForward ? " --no-ff" : "");
        run(description, () -> commandExecutor.merge(source, noFastForward));
    }

    private void run(String description, Callable<GraphDiff> work) {
        logLine(description);
        setBusy(true);
        commandService.submit(work, this::onCommandSucceeded, error -> onError(description, error));
    }

    private void onCommandSucceeded(GraphDiff diff) {
        setBusy(false);
        logLine("  ✓ done");
        view.getCommitGraphView().animateDiff(diff);
        RepoSnapshot snapshot = model.snapshot();
        view.getCommitGraphView().syncRefsAndHead(snapshot.commits(), snapshot.headRefName(), snapshot.headCommitId());
        refreshBranchChoices(snapshot);
        refreshHeadAndBranchLabels(snapshot);
        refreshDirtyCount();
    }

    private void onManualRefresh(RepoSnapshot snapshot) {
        setBusy(false);
        logLine("  ✓ done");
        view.getCommitGraphView().renderInitial(snapshot.commits());
        view.getCommitGraphView().syncRefsAndHead(snapshot.commits(), snapshot.headRefName(), snapshot.headCommitId());
        refreshBranchChoices(snapshot);
        refreshHeadAndBranchLabels(snapshot);
        refreshDirtyCount();
    }

    private void onError(String description, Throwable error) {
        setBusy(false);
        logLine("  ✗ failed: " + rootMessage(error));
        ErrorDialogs.show("Command failed", error);
    }

    private void logLine(String text) {
        var items = view.getCommandLog().getItems();
        items.add("[" + LocalTime.now().format(LOG_TIME) + "] " + text);
        view.getCommandLog().scrollTo(items.size() - 1);
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.toString();
    }

    private void refreshBranchChoices(RepoSnapshot snapshot) {
        List<String> names = snapshot.branches().stream().map(BranchRef::name).collect(Collectors.toList());
        var checkoutChoice = view.getCommandPalette().getCheckoutTargetChoice();
        var mergeChoice = view.getCommandPalette().getMergeSourceChoice();
        String currentCheckout = checkoutChoice.getValue();
        String currentMerge = mergeChoice.getValue();
        checkoutChoice.getItems().setAll(names);
        mergeChoice.getItems().setAll(names);
        if (names.contains(currentCheckout)) {
            checkoutChoice.setValue(currentCheckout);
        }
        if (names.contains(currentMerge)) {
            mergeChoice.setValue(currentMerge);
        }
    }

    private void refreshHeadAndBranchLabels(RepoSnapshot snapshot) {
        String branchName = snapshot.headRefName() != null ? snapshot.headRefName() : "(detached HEAD)";
        view.getBranchLabel().setText("Branch: " + branchName);
        ObjectId head = snapshot.headCommitId();
        view.getHeadLabel().setText("HEAD: " + (head != null ? head.abbreviate(7).name() : "-"));
    }

    private void refreshDirtyCount() {
        commandService.submit(commandExecutor::status,
                status -> view.getDirtyLabel().setText("Dirty files: " + status.dirtyFileCount()),
                error -> view.getDirtyLabel().setText("Dirty files: -"));
    }

    private void setBusy(boolean busy) {
        view.getCommandPalette().setDisable(busy);
    }
}
