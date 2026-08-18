package com.gitquest.core.conflict;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeChunk;
import org.eclipse.jgit.merge.MergeChunk.ConflictState;
import org.eclipse.jgit.merge.MergeResult;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ResolveMerger;

/**
 * Reads a conflicted file's three-way diff straight from JGit's own merge
 * machinery — never re-derives it by parsing {@code <<<<<<<}/{@code =======}/
 * {@code >>>>>>>} markers out of the working-tree file, per CLAUDE.md 4.3.
 * Re-runs the same merge in-core (never touching the working directory or
 * index) between HEAD and {@code MERGE_HEAD} to recover the low-level
 * {@link ResolveMerger} chunk data the porcelain merge API doesn't expose —
 * deterministic given the same two inputs, so this reproduces exactly the
 * conflict the pending merge already left behind.
 */
public final class ConflictDiffReader {

    private ConflictDiffReader() {
    }

    public static ConflictFileDiff read(Repository repository, String filePath) throws IOException {
        ObjectId headId = repository.resolve(Constants.HEAD);
        List<ObjectId> mergeHeads = repository.readMergeHeads();
        if (headId == null || mergeHeads == null || mergeHeads.isEmpty()) {
            throw new IllegalStateException("No merge is currently in progress");
        }
        ObjectId theirsId = mergeHeads.get(0);

        Merger merger = MergeStrategy.RECURSIVE.newMerger(repository, true);
        merger.merge(headId, theirsId);
        if (!(merger instanceof ResolveMerger resolveMerger)) {
            throw new IllegalStateException("Unexpected merge strategy result: " + merger.getClass());
        }

        Object rawResult = resolveMerger.getMergeResults().get(filePath);
        if (rawResult == null) {
            throw new IllegalStateException("No text merge result for " + filePath
                    + ". It may be a binary file, or a delete/modify or rename conflict, not a text conflict.");
        }
        @SuppressWarnings("unchecked")
        MergeResult<RawText> fileResult = (MergeResult<RawText>) rawResult;
        return new ConflictFileDiff(filePath, groupIntoHunks(fileResult));
    }

    /**
     * Chunks come from JGit as base/ours/theirs pieces tagged by which sequence they belong to
     * (0=base, 1=ours, 2=theirs); a run of conflicting-state chunks between two NO_CONFLICT chunks
     * is one visual hunk, keyed by sequence index so it doesn't depend on the order JGit happens to
     * emit FIRST/BASE/NEXT in.
     */
    private static List<ConflictHunk> groupIntoHunks(MergeResult<RawText> fileResult) {
        List<RawText> sequences = fileResult.getSequences();
        List<ConflictHunk> hunks = new ArrayList<>();
        String[] pending = new String[3];
        boolean inConflict = false;

        for (MergeChunk chunk : fileResult) {
            String text = chunkText(sequences, chunk);
            if (chunk.getConflictState() == ConflictState.NO_CONFLICT) {
                if (inConflict) {
                    hunks.add(flush(pending));
                    pending = new String[3];
                    inConflict = false;
                }
                hunks.add(new ConflictHunk(text, text, text, false));
            } else {
                inConflict = true;
                pending[chunk.getSequenceIndex()] = text;
            }
        }
        if (inConflict) {
            hunks.add(flush(pending));
        }
        return hunks;
    }

    private static ConflictHunk flush(String[] pending) {
        return new ConflictHunk(orEmpty(pending[0]), orEmpty(pending[1]), orEmpty(pending[2]), true);
    }

    private static String orEmpty(String text) {
        return text != null ? text : "";
    }

    private static String chunkText(List<RawText> sequences, MergeChunk chunk) {
        if (chunk.getBegin() >= chunk.getEnd()) {
            return "";
        }
        return sequences.get(chunk.getSequenceIndex()).getString(chunk.getBegin(), chunk.getEnd(), false);
    }
}
