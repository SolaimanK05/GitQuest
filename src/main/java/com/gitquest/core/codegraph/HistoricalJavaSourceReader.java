package com.gitquest.core.codegraph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Reads every {@code .java} file's content straight from a commit's
 * recorded tree object via {@link TreeWalk} — never checks anything out, so
 * it never touches the live working directory. This is what lets the Code
 * Graph's time-travel scrubber re-analyze a past commit's dependency graph
 * without disturbing whatever the user is actually working on (same
 * approach as {@code com.gitquest.core.codebase.HistoricalTreeReader}, but
 * returning full file content instead of just sizes, since
 * {@link JavaDependencyAnalyzer} needs to actually parse each file).
 */
public final class HistoricalJavaSourceReader {

    private HistoricalJavaSourceReader() {
    }

    public static Map<String, String> read(Repository repository, ObjectId commitId) throws IOException {
        Map<String, String> sourceByPath = new LinkedHashMap<>();
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            try (TreeWalk walk = new TreeWalk(repository)) {
                walk.addTree(commit.getTree());
                walk.setRecursive(true);
                while (walk.next()) {
                    String path = walk.getPathString();
                    if (!path.endsWith(".java")) {
                        continue;
                    }
                    byte[] bytes = repository.open(walk.getObjectId(0)).getBytes();
                    sourceByPath.put(path, new String(bytes, StandardCharsets.UTF_8));
                }
            }
        }
        return sourceByPath;
    }
}
