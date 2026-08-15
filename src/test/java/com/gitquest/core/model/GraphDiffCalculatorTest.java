package com.gitquest.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.junit.jupiter.api.Test;

import com.gitquest.core.model.GraphDiff.RefChangeType;

class GraphDiffCalculatorTest {

    private static final ObjectId COMMIT_1 = ObjectId.fromString("a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1a1");
    private static final ObjectId COMMIT_2 = ObjectId.fromString("b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2b2");

    @Test
    void noChangesProducesEmptyDiff() {
        CommitNode commit = commitAt(COMMIT_1, 0, 0);
        RepoSnapshot snapshot = new RepoSnapshot(List.of(commit), List.of(), "main", COMMIT_1);

        GraphDiff diff = GraphDiffCalculator.diff(snapshot, snapshot);

        assertTrue(diff.isEmpty());
    }

    @Test
    void newCommitIsReportedAsAdded() {
        CommitNode existing = commitAt(COMMIT_1, 0, 0);
        CommitNode added = commitAt(COMMIT_2, 0, 1);
        RepoSnapshot before = new RepoSnapshot(List.of(existing), List.of(), "main", COMMIT_1);
        RepoSnapshot after = new RepoSnapshot(List.of(existing, added), List.of(), "main", COMMIT_2);

        GraphDiff diff = GraphDiffCalculator.diff(before, after);

        assertEquals(1, diff.addedCommits().size());
        assertEquals(COMMIT_2, diff.addedCommits().get(0).id());
        assertTrue(diff.laneShifts().isEmpty());
        assertNotNull(diff.headChange());
        assertEquals(COMMIT_1, diff.headChange().oldCommitId());
        assertEquals(COMMIT_2, diff.headChange().newCommitId());
    }

    @Test
    void laneChangeIsReportedAsShift() {
        CommitNode beforeCommit = commitAt(COMMIT_1, 0, 0);
        CommitNode afterCommit = commitAt(COMMIT_1, 1, 0);
        RepoSnapshot before = new RepoSnapshot(List.of(beforeCommit), List.of(), "main", COMMIT_1);
        RepoSnapshot after = new RepoSnapshot(List.of(afterCommit), List.of(), "main", COMMIT_1);

        GraphDiff diff = GraphDiffCalculator.diff(before, after);

        assertEquals(1, diff.laneShifts().size());
        assertEquals(0, diff.laneShifts().get(0).oldLane());
        assertEquals(1, diff.laneShifts().get(0).newLane());
        assertNull(diff.headChange());
    }

    @Test
    void newBranchIsReportedAsAddedRef() {
        RepoSnapshot before = new RepoSnapshot(List.of(), List.of(), "main", COMMIT_1);
        BranchRef feature = new BranchRef("feature", COMMIT_1, false, false);
        RepoSnapshot after = new RepoSnapshot(List.of(), List.of(feature), "main", COMMIT_1);

        GraphDiff diff = GraphDiffCalculator.diff(before, after);

        assertEquals(1, diff.refChanges().size());
        assertEquals(RefChangeType.ADDED, diff.refChanges().get(0).type());
        assertEquals("feature", diff.refChanges().get(0).refName());
    }

    private static CommitNode commitAt(ObjectId id, int lane, int sequenceIndex) {
        return new CommitNode(id, "message", "author", 0L, lane, sequenceIndex, List.of(), List.of());
    }
}
