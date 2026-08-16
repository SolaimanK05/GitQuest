package com.gitquest.core.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotCommitList;
import org.eclipse.jgit.revplot.PlotLane;
import org.eclipse.jgit.revplot.PlotWalk;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Wraps a JGit {@link Repository}/{@link Git} for one open session and
 * exposes commits/branches/HEAD as observable state the UI can bind to.
 *
 * <p>{@link #refresh()} performs blocking disk/history I/O and must be
 * called off the JavaFX Application Thread (see {@code CommandService}).
 * It computes the new state into plain fields synchronously on the calling
 * thread, then publishes those fields into the {@code Observable*} state on
 * the FX thread via {@link Platform#runLater}. {@link #snapshot()} always
 * reads the plain fields (not the observable lists), so a snapshot taken
 * right after {@code refresh()} returns is guaranteed current even if the
 * FX-thread publish hasn't run yet.
 */
public final class RepoStateModel {

    private final Repository repository;
    private final Git git;

    private final ObservableList<CommitNode> commits = FXCollections.observableArrayList();
    private final ObservableList<BranchRef> branches = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper headBranchName = new ReadOnlyStringWrapper();
    private final ReadOnlyObjectWrapper<ObjectId> headCommitId = new ReadOnlyObjectWrapper<>();

    private volatile List<CommitNode> latestCommits = List.of();
    private volatile List<BranchRef> latestBranches = List.of();
    private volatile String latestHeadRefName;
    private volatile ObjectId latestHeadCommitId;

    public RepoStateModel(Repository repository) {
        this.repository = repository;
        this.git = new Git(repository);
    }

    public Repository getRepository() {
        return repository;
    }

    public Git getGit() {
        return git;
    }

    public ObservableList<CommitNode> getCommits() {
        return commits;
    }

    public ObservableList<BranchRef> getBranches() {
        return branches;
    }

    public ReadOnlyStringProperty headBranchNameProperty() {
        return headBranchName.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<ObjectId> headCommitIdProperty() {
        return headCommitId.getReadOnlyProperty();
    }

    /**
     * Re-walks history via {@code PlotWalk}/{@code PlotCommitList} so lane
     * layout comes from JGit directly. Blocking; call off the FX thread.
     */
    public void refresh() throws IOException {
        List<CommitNode> newCommits = new ArrayList<>();
        List<BranchRef> newBranches;
        String newHeadRefName;
        ObjectId newHeadCommitId;

        try (PlotWalk walk = new PlotWalk(repository)) {
            Map<ObjectId, List<String>> refNamesByCommit = new HashMap<>();
            List<BranchRef> collectedBranches = new ArrayList<>();

            Ref headRef = repository.exactRef(Constants.HEAD);
            String headTargetName = (headRef != null && headRef.isSymbolic())
                    ? headRef.getTarget().getName()
                    : null;
            newHeadRefName = headTargetName != null ? Repository.shortenRefName(headTargetName) : null;
            newHeadCommitId = repository.resolve(Constants.HEAD);

            // The current branch marks its start point first so JGit's own PlotWalk lane algorithm
            // keeps IT as the straight "trunk" lane a divergent sibling branch reads as branching
            // off of — otherwise plain ref order (alphabetical, from getRefsByPrefix) decides
            // arbitrarily, and a same-named-earlier branch can end up looking like the continuation
            // instead of whatever's actually checked out.
            List<Ref> branchRefs = new ArrayList<>(repository.getRefDatabase().getRefsByPrefix(Constants.R_HEADS));
            branchRefs.sort(Comparator.comparing(
                    ref -> !Repository.shortenRefName(ref.getName()).equals(newHeadRefName)));

            for (Ref ref : branchRefs) {
                ObjectId commitId = ref.getObjectId();
                if (commitId == null) {
                    continue;
                }
                walk.markStart(walk.parseCommit(commitId));
                String shortName = Repository.shortenRefName(ref.getName());
                collectedBranches.add(new BranchRef(shortName, commitId, false, false));
                refNamesByCommit.computeIfAbsent(commitId, unused -> new ArrayList<>()).add(shortName);
            }

            newBranches = new ArrayList<>();
            for (BranchRef branch : collectedBranches) {
                boolean isHead = branch.name().equals(newHeadRefName);
                newBranches.add(new BranchRef(branch.name(), branch.targetId(), isHead, branch.isRemote()));
            }

            PlotCommitList<PlotLane> plotList = new PlotCommitList<>();
            plotList.source(walk);
            plotList.fillTo(Integer.MAX_VALUE);

            // PlotCommitList walks newest-first, and a child is always emitted before its
            // parents — a valid topological order. Reverse it (rather than re-sorting by
            // commit-time, which is only second-granularity and ties arbitrarily for commits
            // made within the same second) so sequenceIndex renders oldest-at-top,
            // topologically-consistent top-to-bottom. A brand new commit is then always
            // appended at the bottom (highest index) rather than shifting every existing
            // commit's row down by one — appended commits animate in, nothing else moves.
            List<PlotCommit<PlotLane>> chronological = new ArrayList<>(plotList.size());
            for (int i = plotList.size() - 1; i >= 0; i--) {
                chronological.add(plotList.get(i));
            }

            for (int i = 0; i < chronological.size(); i++) {
                PlotCommit<PlotLane> plotCommit = chronological.get(i);
                List<ObjectId> parentIds = new ArrayList<>();
                for (int p = 0; p < plotCommit.getParentCount(); p++) {
                    parentIds.add(plotCommit.getParent(p).getId());
                }
                int lane = plotCommit.getLane() != null ? plotCommit.getLane().getPosition() : 0;
                String authorName = plotCommit.getAuthorIdent() != null
                        ? plotCommit.getAuthorIdent().getName()
                        : "";
                newCommits.add(new CommitNode(
                        plotCommit.getId(),
                        plotCommit.getShortMessage(),
                        authorName,
                        plotCommit.getCommitTime(),
                        lane,
                        i,
                        parentIds,
                        refNamesByCommit.getOrDefault(plotCommit.getId(), List.of())));
            }
        }

        this.latestCommits = List.copyOf(newCommits);
        this.latestBranches = List.copyOf(newBranches);
        this.latestHeadRefName = newHeadRefName;
        this.latestHeadCommitId = newHeadCommitId;

        List<CommitNode> commitsForUi = this.latestCommits;
        List<BranchRef> branchesForUi = this.latestBranches;
        Platform.runLater(() -> {
            commits.setAll(commitsForUi);
            branches.setAll(branchesForUi);
            headBranchName.set(newHeadRefName);
            headCommitId.set(newHeadCommitId);
        });
    }

    /** Cheap copy of the last {@link #refresh()} result, for diffing. No I/O. */
    public RepoSnapshot snapshot() {
        return new RepoSnapshot(latestCommits, latestBranches, latestHeadRefName, latestHeadCommitId);
    }
}
