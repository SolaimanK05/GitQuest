package com.gitquest.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jgit.lib.ObjectId;

import com.gitquest.core.model.GraphDiff.HeadChange;
import com.gitquest.core.model.GraphDiff.LaneShift;
import com.gitquest.core.model.GraphDiff.RefChange;
import com.gitquest.core.model.GraphDiff.RefChangeType;

/**
 * Pure diffing of two {@link RepoSnapshot}s into a {@link GraphDiff}. No JGit
 * calls, no I/O — safe to unit test without a real repository or the JavaFX
 * toolkit.
 */
public final class GraphDiffCalculator {

    private GraphDiffCalculator() {
    }

    public static GraphDiff diff(RepoSnapshot before, RepoSnapshot after) {
        Map<ObjectId, CommitNode> beforeCommits = indexById(before.commits());
        Map<ObjectId, CommitNode> afterCommits = indexById(after.commits());

        List<CommitNode> addedCommits = new ArrayList<>();
        List<LaneShift> laneShifts = new ArrayList<>();
        for (CommitNode afterNode : after.commits()) {
            CommitNode beforeNode = beforeCommits.get(afterNode.id());
            if (beforeNode == null) {
                addedCommits.add(afterNode);
            } else if (beforeNode.lane() != afterNode.lane()) {
                laneShifts.add(new LaneShift(afterNode.id(), beforeNode.lane(), afterNode.lane()));
            }
        }

        List<ObjectId> removedCommitIds = new ArrayList<>();
        for (CommitNode beforeNode : before.commits()) {
            if (!afterCommits.containsKey(beforeNode.id())) {
                removedCommitIds.add(beforeNode.id());
            }
        }

        List<RefChange> refChanges = diffRefs(before.branches(), after.branches());

        HeadChange headChange = diffHead(before, after);

        return new GraphDiff(addedCommits, removedCommitIds, laneShifts, refChanges, headChange);
    }

    private static Map<ObjectId, CommitNode> indexById(List<CommitNode> commits) {
        Map<ObjectId, CommitNode> byId = new HashMap<>();
        for (CommitNode commit : commits) {
            byId.put(commit.id(), commit);
        }
        return byId;
    }

    private static List<RefChange> diffRefs(List<BranchRef> before, List<BranchRef> after) {
        Map<String, BranchRef> beforeByName = new HashMap<>();
        for (BranchRef ref : before) {
            beforeByName.put(ref.name(), ref);
        }
        Map<String, BranchRef> afterByName = new HashMap<>();
        for (BranchRef ref : after) {
            afterByName.put(ref.name(), ref);
        }

        List<RefChange> changes = new ArrayList<>();
        for (BranchRef afterRef : after) {
            BranchRef beforeRef = beforeByName.get(afterRef.name());
            if (beforeRef == null) {
                changes.add(new RefChange(afterRef.name(), null, afterRef.targetId(), RefChangeType.ADDED));
            } else if (!Objects.equals(beforeRef.targetId(), afterRef.targetId())) {
                changes.add(new RefChange(afterRef.name(), beforeRef.targetId(), afterRef.targetId(), RefChangeType.MOVED));
            }
        }
        for (BranchRef beforeRef : before) {
            if (!afterByName.containsKey(beforeRef.name())) {
                changes.add(new RefChange(beforeRef.name(), beforeRef.targetId(), null, RefChangeType.REMOVED));
            }
        }
        return changes;
    }

    private static HeadChange diffHead(RepoSnapshot before, RepoSnapshot after) {
        boolean refChanged = !Objects.equals(before.headRefName(), after.headRefName());
        boolean commitChanged = !Objects.equals(before.headCommitId(), after.headCommitId());
        if (!refChanged && !commitChanged) {
            return null;
        }
        return new HeadChange(before.headRefName(), after.headRefName(), before.headCommitId(), after.headCommitId());
    }
}
