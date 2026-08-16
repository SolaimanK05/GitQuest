package com.gitquest.core.conflict;

/**
 * One aligned region of a conflicted file's three-way diff. For a
 * non-conflicting hunk, {@code baseText}/{@code oursText}/{@code theirsText}
 * are all identical (the surrounding, unchanged context). For a conflicting
 * hunk, they may differ — an absent side (e.g. a pure addition with nothing
 * in base) reads as an empty string, not null.
 */
public record ConflictHunk(String baseText, String oursText, String theirsText, boolean conflicting) {
}
