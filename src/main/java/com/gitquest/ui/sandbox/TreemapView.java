package com.gitquest.ui.sandbox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import com.gitquest.core.codebase.FileEntry;
import com.gitquest.core.codebase.TreemapLayout;
import com.gitquest.core.codebase.TreemapLayout.PlacedEntry;
import com.gitquest.core.codebase.TreemapLayout.Rect;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Renders a {@link FileEntry} tree as a treemap (CLAUDE.md 4.3): one colored
 * cell per file, area proportional to size, nested inside thin directory
 * outlines. Re-renders (overlay toggle, time-travel scrubber, working-tree
 * edits) morph existing cells to their new rect/color via {@link Timeline}
 * rather than clearing and redrawing, per CLAUDE.md Section 6 — cells are
 * keyed by relative path across renders so identity survives a re-layout.
 */
public final class TreemapView extends Pane {

    private static final Duration MORPH_DURATION = Duration.millis(420);
    private static final double MIN_LABEL_WIDTH = 46;
    private static final double MIN_LABEL_HEIGHT = 16;
    private static final Color BORDER_COLOR = Color.web("#0C0C0D");
    private static final Color DIRECTORY_OUTLINE = Color.web("#9DA5B4");
    private static final Color SELECTED_STROKE = Color.web("#F05133");

    private final Group bordersLayer = new Group();
    private final Group cellsLayer = new Group();
    private final Group directoryLabelsLayer = new Group();
    private final Map<String, Rectangle> rectsByPath = new HashMap<>();
    private final Map<String, Label> labelsByPath = new HashMap<>();

    private Consumer<FileEntry> onFileSelected = entry -> { };
    private Function<FileEntry, Color> colorFunction = entry -> Color.web("#3A6EA5");
    private String selectedPath;

    public TreemapView() {
        getStyleClass().add("commit-graph");
        // Directory name tags must draw on top of file cells (not just their outlines),
        // since a directory's inset gap is thinner than the label — otherwise the first
        // child cell paints right over it.
        getChildren().addAll(bordersLayer, cellsLayer, directoryLabelsLayer);
    }

    public void setOnFileSelected(Consumer<FileEntry> handler) {
        this.onFileSelected = handler;
    }

    public void setColorFunction(Function<FileEntry, Color> colorFunction) {
        this.colorFunction = colorFunction;
    }

    /** Lays {@code root} out to fill {@code width}x{@code height} and morphs every cell to its new rect/color. */
    public void render(FileEntry root, double width, double height) {
        setPrefSize(width, height);
        List<PlacedEntry> placed = TreemapLayout.layout(root, 0, 0, width, height);

        redrawDirectoryBorders(placed);

        Set<String> seen = new HashSet<>();
        ParallelTransition morph = new ParallelTransition();
        for (PlacedEntry placedEntry : placed) {
            FileEntry entry = placedEntry.entry();
            if (entry.directory() && !entry.children().isEmpty()) {
                continue; // only leaf files (and empty dirs) get a colored, clickable cell
            }
            seen.add(entry.relativePath());
            morph.getChildren().addAll(placeOrMorph(entry, placedEntry.rect()));
        }

        for (String path : new HashSet<>(rectsByPath.keySet())) {
            if (!seen.contains(path)) {
                fadeOutAndRemove(path);
            }
        }

        morph.play();
    }

    private void redrawDirectoryBorders(List<PlacedEntry> placed) {
        bordersLayer.getChildren().clear();
        directoryLabelsLayer.getChildren().clear();
        for (PlacedEntry placedEntry : placed) {
            FileEntry entry = placedEntry.entry();
            if (placedEntry.depth() == 0 || !entry.directory() || entry.children().isEmpty()) {
                continue;
            }
            Rect rect = placedEntry.rect();
            Rectangle outline = new Rectangle(rect.x(), rect.y(), rect.w(), rect.h());
            outline.setFill(Color.TRANSPARENT);
            outline.setStroke(DIRECTORY_OUTLINE);
            outline.setStrokeWidth(1.5);
            outline.setMouseTransparent(true);
            bordersLayer.getChildren().add(outline);

            if (rect.w() >= MIN_LABEL_WIDTH && rect.h() >= MIN_LABEL_HEIGHT) {
                Label folderLabel = new Label(entry.name() + "/");
                folderLabel.setMouseTransparent(true);
                folderLabel.setStyle("-fx-font-size: 10px; -fx-font-style: italic; -fx-text-fill: #E6E6E6;"
                        + " -fx-background-color: rgba(12,12,13,0.85); -fx-padding: 0 3 0 3;"
                        + " -fx-background-radius: 2px;");
                // Straddles the directory's top border like a tab, rather than sitting inside
                // the inset gap — that gap is thinner than the label, so it would otherwise
                // collide with the first child cell's own name label.
                folderLabel.setLayoutX(rect.x() + 3);
                folderLabel.setLayoutY(Math.max(2, rect.y() - 8));
                directoryLabelsLayer.getChildren().add(folderLabel);
            }
        }
    }

    private List<Animation> placeOrMorph(FileEntry entry, Rect rect) {
        List<Animation> animations = new ArrayList<>();
        Rectangle rectangle = rectsByPath.get(entry.relativePath());
        Label label = labelsByPath.get(entry.relativePath());

        if (rectangle == null) {
            rectangle = newCell(entry, rect);
            rectsByPath.put(entry.relativePath(), rectangle);
            label = new Label();
            label.setMouseTransparent(true);
            label.setStyle("-fx-font-size: 10px; -fx-text-fill: white;");
            labelsByPath.put(entry.relativePath(), label);
            cellsLayer.getChildren().addAll(rectangle, label);

            FadeTransition fadeIn = new FadeTransition(MORPH_DURATION, rectangle);
            fadeIn.setToValue(1);
            animations.add(fadeIn);
        }

        boolean isSelected = entry.relativePath().equals(selectedPath);
        rectangle.setFill(colorFunction.apply(entry));
        rectangle.setStroke(isSelected ? SELECTED_STROKE : BORDER_COLOR);
        rectangle.setStrokeWidth(isSelected ? 2.5 : 1);
        Tooltip.install(rectangle, new Tooltip(entry.relativePath().isEmpty() ? entry.name() : entry.relativePath()
                + "\n" + humanSize(entry.size())));

        Timeline resize = new Timeline(new KeyFrame(MORPH_DURATION,
                new KeyValue(rectangle.xProperty(), rect.x(), Interpolator.EASE_BOTH),
                new KeyValue(rectangle.yProperty(), rect.y(), Interpolator.EASE_BOTH),
                new KeyValue(rectangle.widthProperty(), rect.w(), Interpolator.EASE_BOTH),
                new KeyValue(rectangle.heightProperty(), rect.h(), Interpolator.EASE_BOTH)));
        animations.add(resize);

        boolean showLabel = rect.w() >= MIN_LABEL_WIDTH && rect.h() >= MIN_LABEL_HEIGHT;
        label.setText(showLabel ? entry.name() : "");
        Timeline moveLabel = new Timeline(new KeyFrame(MORPH_DURATION,
                new KeyValue(label.layoutXProperty(), rect.x() + 4, Interpolator.EASE_BOTH),
                new KeyValue(label.layoutYProperty(), rect.y() + 2, Interpolator.EASE_BOTH)));
        animations.add(moveLabel);

        return animations;
    }

    private Rectangle newCell(FileEntry entry, Rect rect) {
        Rectangle rectangle = new Rectangle();
        double cx = rect.x() + rect.w() / 2;
        double cy = rect.y() + rect.h() / 2;
        rectangle.setX(cx);
        rectangle.setY(cy);
        rectangle.setWidth(0);
        rectangle.setHeight(0);
        rectangle.setOpacity(0);
        rectangle.setStroke(BORDER_COLOR);
        rectangle.setStrokeWidth(1);
        rectangle.setCursor(Cursor.HAND);
        rectangle.setOnMouseClicked(e -> selectFile(entry));
        return rectangle;
    }

    private void selectFile(FileEntry entry) {
        String previous = selectedPath;
        selectedPath = entry.relativePath();
        if (previous != null) {
            Rectangle previousRect = rectsByPath.get(previous);
            if (previousRect != null) {
                previousRect.setStroke(BORDER_COLOR);
                previousRect.setStrokeWidth(1);
            }
        }
        Rectangle rectangle = rectsByPath.get(selectedPath);
        if (rectangle != null) {
            rectangle.setStroke(SELECTED_STROKE);
            rectangle.setStrokeWidth(2.5);
        }
        onFileSelected.accept(entry);
    }

    private void fadeOutAndRemove(String path) {
        Rectangle rectangle = rectsByPath.remove(path);
        Label label = labelsByPath.remove(path);
        if (rectangle == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(MORPH_DURATION, rectangle);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            cellsLayer.getChildren().remove(rectangle);
            if (label != null) {
                cellsLayer.getChildren().remove(label);
            }
        });
        fade.play();
        if (label != null) {
            FadeTransition fadeLabel = new FadeTransition(MORPH_DURATION, label);
            fadeLabel.setToValue(0);
            fadeLabel.play();
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        return String.format("%.1f MB", kb / 1024.0);
    }
}
