package com.gitquest.core.command;

import java.util.Set;

/** Read-only working-tree status, wrapping {@code org.eclipse.jgit.api.Status}. */
public record StatusSnapshot(
        Set<String> added,
        Set<String> changed,
        Set<String> modified,
        Set<String> removed,
        Set<String> missing,
        Set<String> untracked,
        Set<String> conflicting) {

    public boolean isClean() {
        return added.isEmpty() && changed.isEmpty() && modified.isEmpty()
                && removed.isEmpty() && missing.isEmpty() && untracked.isEmpty()
                && conflicting.isEmpty();
    }

    public int dirtyFileCount() {
        return added.size() + changed.size() + modified.size()
                + removed.size() + missing.size() + untracked.size();
    }
}
