package com.gitquest.core.codebase;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a {@link FileEntry} tree by walking the live working directory on
 * disk (excluding {@code .git}) — the Codebase visualizer's "current" state,
 * per CLAUDE.md 4.3. Blocking disk I/O; callers must run this off the
 * JavaFX Application Thread.
 */
public final class WorkingTreeScanner {

    private WorkingTreeScanner() {
    }

    public static FileEntry scan(Path root) {
        return scanDirectory(root, "");
    }

    private static FileEntry scanDirectory(Path dir, String relativePath) {
        List<FileEntry> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (name.equals(".git")) {
                    continue;
                }
                String childRelative = relativePath.isEmpty() ? name : relativePath + "/" + name;
                if (Files.isDirectory(entry)) {
                    children.add(scanDirectory(entry, childRelative));
                } else {
                    children.add(FileEntry.file(name, childRelative, readSize(entry)));
                }
            }
        } catch (IOException e) {
            // Unreadable directory: treat as empty rather than failing the whole scan.
        }
        children.sort(Comparator.comparing(FileEntry::name, String.CASE_INSENSITIVE_ORDER));
        return FileEntry.directory(dir.getFileName().toString(), relativePath, children);
    }

    private static long readSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0;
        }
    }
}
