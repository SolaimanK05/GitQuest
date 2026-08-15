package com.gitquest.ui.sandbox;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

import com.gitquest.core.command.StatusSnapshot;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.util.Callback;

/**
 * Plain working-tree file browser: builds {@code TreeItem<Path>} nodes from
 * a directory walk (excluding {@code .git}) and a cell factory that colors
 * modified/added/untracked entries using the latest {@link StatusSnapshot}.
 * Deliberately not a custom {@code Node} subclass — the {@code TreeView}
 * itself stays a stock control, fully Scene-Builder-editable. No live
 * filesystem watching yet (that's the later {@code WatchService} phase per
 * CLAUDE.md); callers rebuild the tree after each command instead.
 */
final class FileTreeBuilder {

    private FileTreeBuilder() {
    }

    static TreeItem<Path> buildTree(Path root) {
        TreeItem<Path> rootItem = new TreeItem<>(root);
        rootItem.setExpanded(true);
        addChildren(rootItem, root);
        return rootItem;
    }

    private static void addChildren(TreeItem<Path> parentItem, Path dir) {
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (entry.getFileName().toString().equals(".git")) {
                    continue;
                }
                entries.add(entry);
            }
        } catch (IOException e) {
            return;
        }
        entries.sort(Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString().toLowerCase()));

        for (Path entry : entries) {
            TreeItem<Path> item = new TreeItem<>(entry);
            parentItem.getChildren().add(item);
            if (Files.isDirectory(entry)) {
                addChildren(item, entry);
            }
        }
    }

    /** Cell factory: folder/file glyph + name, colored by working-tree status. */
    static Callback<TreeView<Path>, TreeCell<Path>> cellFactory(Path repoRoot, Supplier<StatusSnapshot> statusSupplier) {
        return treeView -> new TreeCell<>() {
            @Override
            protected void updateItem(Path path, boolean empty) {
                super.updateItem(path, empty);
                if (empty || path == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                boolean isDir = Files.isDirectory(path);
                String glyph = isDir ? "📁 " : "📄 "; // folder / file
                setText(glyph + path.getFileName().toString());

                String relative = repoRoot.relativize(path).toString().replace('\\', '/');
                StatusSnapshot status = statusSupplier.get();
                setStyle(status != null && isDirty(status, relative) ? "-fx-text-fill: #FFC857;" : "");
            }
        };
    }

    private static boolean isDirty(StatusSnapshot status, String relativePath) {
        return status.added().contains(relativePath)
                || status.changed().contains(relativePath)
                || status.modified().contains(relativePath)
                || status.untracked().contains(relativePath)
                || status.missing().contains(relativePath)
                || status.removed().contains(relativePath);
    }
}
