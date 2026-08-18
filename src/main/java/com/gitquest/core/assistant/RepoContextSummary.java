package com.gitquest.core.assistant;

import java.util.List;
import java.util.stream.Collectors;

import com.gitquest.core.command.StatusSnapshot;
import com.gitquest.core.model.BranchRef;
import com.gitquest.core.model.CommitNode;
import com.gitquest.core.model.RepoSnapshot;

/**
 * Builds a short plain-text description of the sandbox's current state to hand Gemini as
 * context — current branch, HEAD, recent commits, dirty/conflicted files — so it can answer
 * questions about *this* repo ("why is my branch diverged") and not just Git in the abstract.
 */
public final class RepoContextSummary {

    private static final int MAX_RECENT_COMMITS = 8;

    private RepoContextSummary() {
    }

    public static String build(RepoSnapshot snapshot, StatusSnapshot status) {
        StringBuilder out = new StringBuilder();
        out.append("Current sandbox repository state:\n");
        out.append("- Branch: ").append(snapshot.headRefName() != null ? snapshot.headRefName() : "(detached HEAD)").append('\n');
        out.append("- HEAD: ").append(snapshot.headCommitId() != null ? snapshot.headCommitId().abbreviate(7).name() : "(no commits yet)").append('\n');
        out.append("- Branches: ").append(snapshot.branches().stream().map(BranchRef::name).collect(Collectors.joining(", "))).append('\n');

        // snapshot.commits() is oldest-first (see RepoStateModel.refresh) -- take the tail, then
        // walk it backwards so the summary reads newest-first, the order a person would expect.
        List<CommitNode> all = snapshot.commits();
        out.append("- Total commits: ").append(all.size()).append('\n');
        List<CommitNode> recentOldestFirst = all.subList(Math.max(0, all.size() - MAX_RECENT_COMMITS), all.size());
        if (recentOldestFirst.isEmpty()) {
            out.append("- No commits yet.\n");
        } else {
            out.append("- Most recent ").append(recentOldestFirst.size()).append(" commit(s), newest first:\n");
            for (int i = recentOldestFirst.size() - 1; i >= 0; i--) {
                CommitNode commit = recentOldestFirst.get(i);
                out.append("    ").append(commit.id().abbreviate(7).name())
                        .append(" \"").append(commit.shortMessage()).append("\"\n");
            }
        }

        if (status != null) {
            out.append("- Dirty files: ").append(status.dirtyFileCount()).append('\n');
            if (!status.conflicting().isEmpty()) {
                out.append("- CONFLICTED files (merge pending): ").append(String.join(", ", status.conflicting())).append('\n');
            }
        }
        return out.toString();
    }
}
