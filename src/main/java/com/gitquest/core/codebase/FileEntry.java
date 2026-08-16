package com.gitquest.core.codebase;

import java.util.List;

/**
 * One node in a file-tree snapshot — either a leaf file (with its byte size)
 * or a directory (whose size is the sum of its children's). Deliberately
 * source-agnostic: the same shape is built both from the live working
 * directory ({@code WorkingTreeScanner}) and from a historical commit's tree
 * ({@code HistoricalTreeReader}), so {@code TreemapLayout} and the treemap
 * view don't need to care which one they're rendering.
 */
public record FileEntry(String name, String relativePath, boolean directory, long size, List<FileEntry> children) {

    public static FileEntry file(String name, String relativePath, long size) {
        return new FileEntry(name, relativePath, false, size, List.of());
    }

    public static FileEntry directory(String name, String relativePath, List<FileEntry> children) {
        long total = 0;
        for (FileEntry child : children) {
            total += child.size();
        }
        return new FileEntry(name, relativePath, true, total, List.copyOf(children));
    }
}
