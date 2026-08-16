package com.gitquest.core.codebase;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Reads a {@link FileEntry} tree straight from a commit's recorded tree
 * object via {@link TreeWalk} — never checks anything out, so it never
 * touches the live working directory. This is what lets the time-travel
 * scrubber (CLAUDE.md 4.3) move through history without disturbing whatever
 * the user is actually working on.
 */
public final class HistoricalTreeReader {

    private HistoricalTreeReader() {
    }

    public static FileEntry read(Repository repository, ObjectId commitId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            MutableNode root = new MutableNode("", "");
            try (TreeWalk walk = new TreeWalk(repository)) {
                walk.addTree(commit.getTree());
                walk.setRecursive(true);
                while (walk.next()) {
                    long size = repository.open(walk.getObjectId(0)).getSize();
                    insert(root, walk.getPathString(), size);
                }
            }
            return root.toFileEntry();
        }
    }

    private static void insert(MutableNode root, String path, long size) {
        String[] parts = path.split("/");
        MutableNode current = root;
        StringBuilder relative = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                relative.append('/');
            }
            relative.append(parts[i]);
            boolean isLast = i == parts.length - 1;
            if (isLast) {
                current.children.add(MutableNode.file(parts[i], relative.toString(), size));
            } else {
                current = current.childDirectory(parts[i], relative.toString());
            }
        }
    }

    /** Builder-only intermediate — {@link FileEntry} itself stays an immutable record. */
    private static final class MutableNode {
        final String name;
        final String relativePath;
        final List<MutableNode> children = new ArrayList<>();
        final long fileSize;
        final boolean isFile;

        MutableNode(String name, String relativePath) {
            this(name, relativePath, -1, false);
        }

        private MutableNode(String name, String relativePath, long fileSize, boolean isFile) {
            this.name = name;
            this.relativePath = relativePath;
            this.fileSize = fileSize;
            this.isFile = isFile;
        }

        static MutableNode file(String name, String relativePath, long size) {
            return new MutableNode(name, relativePath, size, true);
        }

        MutableNode childDirectory(String name, String relativePath) {
            for (MutableNode child : children) {
                if (!child.isFile && child.name.equals(name)) {
                    return child;
                }
            }
            MutableNode created = new MutableNode(name, relativePath);
            children.add(created);
            return created;
        }

        FileEntry toFileEntry() {
            if (isFile) {
                return FileEntry.file(name, relativePath, fileSize);
            }
            List<FileEntry> converted = new ArrayList<>(children.size());
            for (MutableNode child : children) {
                converted.add(child.toFileEntry());
            }
            return FileEntry.directory(name, relativePath, converted);
        }
    }
}
