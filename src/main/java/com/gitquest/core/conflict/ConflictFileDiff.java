package com.gitquest.core.conflict;

import java.util.List;

/** A conflicted file's full three-way diff, as an ordered sequence of hunks (see {@link ConflictHunk}). */
public record ConflictFileDiff(String filePath, List<ConflictHunk> hunks) {

    /** The file's content if you resolved every conflicting hunk by keeping "ours". */
    public String oursContent() {
        return reconstruct(ConflictHunk::oursText);
    }

    /** The file's content if you resolved every conflicting hunk by keeping "theirs". */
    public String theirsContent() {
        return reconstruct(ConflictHunk::theirsText);
    }

    private String reconstruct(java.util.function.Function<ConflictHunk, String> side) {
        StringBuilder text = new StringBuilder();
        for (ConflictHunk hunk : hunks) {
            text.append(side.apply(hunk));
        }
        return text.toString();
    }
}
