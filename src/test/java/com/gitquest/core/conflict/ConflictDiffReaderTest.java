package com.gitquest.core.conflict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand.FastForwardMode;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;

class ConflictDiffReaderTest {

    private static final PersonIdent AUTHOR = new PersonIdent("A", "a@x.com");

    @Test
    void readsBaseOursTheirsForAConflictingLine() throws Exception {
        Path root = Files.createTempDirectory("conflict-diff-test");
        try (Git git = Git.init().setDirectory(root.toFile()).call()) {
            Path notes = root.resolve("notes.txt");
            Files.writeString(notes, "line1\noriginal line2\nline3\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("base").setAuthor(AUTHOR).call();

            git.branchCreate().setName("feature").call();
            git.checkout().setName("feature").call();
            Files.writeString(notes, "line1\nfeature line2\nline3\n");
            git.add().addFilepattern(".").call();
            RevCommit theirs = git.commit().setMessage("feature edits line2").setAuthor(AUTHOR).call();

            git.checkout().setName("main").call();
            Files.writeString(notes, "line1\nmain line2\nline3\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("main edits line2").setAuthor(AUTHOR).call();

            git.merge().include(theirs).setFastForward(FastForwardMode.NO_FF).call();

            ConflictFileDiff diff = ConflictDiffReader.read(git.getRepository(), "notes.txt");

            assertEquals("notes.txt", diff.filePath());
            assertTrue(diff.hunks().size() >= 3, "expected at least [line1] [conflict] [line3]");
            assertFalse(diff.hunks().get(0).conflicting());
            assertEquals("line1\n", diff.hunks().get(0).baseText());

            ConflictHunk conflict = diff.hunks().stream().filter(ConflictHunk::conflicting).findFirst().orElseThrow();
            assertEquals("original line2\n", conflict.baseText());
            assertEquals("main line2\n", conflict.oursText());
            assertEquals("feature line2\n", conflict.theirsText());

            assertEquals("line1\nmain line2\nline3\n", diff.oursContent());
            assertEquals("line1\nfeature line2\nline3\n", diff.theirsContent());
        }
    }

    @Test
    void throwsAMeaningfulErrorWhenNoMergeIsInProgress() throws Exception {
        Path root = Files.createTempDirectory("conflict-diff-test-nomerge");
        try (Git git = Git.init().setDirectory(root.toFile()).call()) {
            Files.writeString(root.resolve("a.txt"), "x\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("c1").setAuthor(AUTHOR).call();

            try {
                ConflictDiffReader.read(git.getRepository(), "a.txt");
                throw new AssertionError("expected IllegalStateException");
            } catch (IllegalStateException expected) {
                // no merge in progress — correctly refused rather than returning nonsense
            }
        }
    }
}
