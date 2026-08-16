package com.gitquest.ui.sandbox;

import java.util.function.Function;

import com.gitquest.core.conflict.ConflictFileDiff;
import com.gitquest.core.conflict.ConflictHunk;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Read-only three-way diff for one conflicted file — base / ours / theirs
 * side by side, driven directly by {@link ConflictFileDiff} (itself read
 * straight from JGit's own merge machinery, per CLAUDE.md 4.3 — this view
 * never parses {@code <<<<<<<} markers). Non-conflicting hunks render
 * plainly; conflicting ones get a distinct highlighted background in all
 * three columns so the disputed region is easy to spot at a glance.
 *
 * <p>Hunks are grouped identically across all three columns, but a hunk's
 * text can span a different number of lines on each side (e.g. ours
 * changed one line, theirs changed two) — rows are aligned hunk-by-hunk,
 * not line-by-line, a deliberate simplification rather than a full
 * line-diff alignment algorithm.
 */
public final class ConflictDiffView extends HBox {

    private static final String CONFLICT_STYLE =
            "-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-text-fill: #FFD9D2; "
                    + "-fx-background-color: rgba(240, 81, 51, 0.22); -fx-padding: 2 4 2 4;";
    private static final String PLAIN_STYLE =
            "-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-text-fill: #9DA5B4; -fx-padding: 2 4 2 4;";
    private static final String EMPTY_CONFLICT_STYLE =
            "-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px; -fx-text-fill: #7A342B; -fx-font-style: italic; "
                    + "-fx-background-color: rgba(240, 81, 51, 0.10); -fx-padding: 2 4 2 4;";

    public ConflictDiffView() {
        getStyleClass().add("commit-graph");
    }

    public void render(ConflictFileDiff diff) {
        getChildren().clear();
        VBox base = column("BASE", diff, ConflictHunk::baseText);
        VBox ours = column("OURS (yours)", diff, ConflictHunk::oursText);
        VBox theirs = column("THEIRS (incoming)", diff, ConflictHunk::theirsText);
        for (VBox column : new VBox[] {base, ours, theirs}) {
            HBox.setHgrow(column, Priority.ALWAYS);
        }
        base.setStyle("-fx-border-color: transparent -border transparent transparent; -fx-border-width: 0 1px 0 0;");
        ours.setStyle("-fx-border-color: transparent -border transparent transparent; -fx-border-width: 0 1px 0 0;");
        getChildren().addAll(base, ours, theirs);
    }

    public void clear() {
        getChildren().clear();
    }

    /** Fallback for conflicts {@link com.gitquest.core.conflict.ConflictDiffReader} can't turn into a text diff — a binary file, or a delete/rename conflict (CLAUDE.md 4.3's accepted edge case for v1). */
    public void showMessage(String message) {
        getChildren().clear();
        Label label = new Label(message);
        label.setWrapText(true);
        label.setPadding(new Insets(16));
        label.getStyleClass().add("sub-label");
        getChildren().add(label);
    }

    private static VBox column(String heading, ConflictFileDiff diff, Function<ConflictHunk, String> textOf) {
        VBox column = new VBox(1);
        column.setPadding(new Insets(8));
        column.setMinWidth(200);
        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add("section-heading");
        column.getChildren().add(headingLabel);
        for (ConflictHunk hunk : diff.hunks()) {
            String text = textOf.apply(hunk);
            boolean empty = text.isEmpty();
            Label line = new Label(empty && hunk.conflicting() ? "(nothing here)" : text);
            line.setWrapText(true);
            line.setMaxWidth(Double.MAX_VALUE);
            line.setStyle(!hunk.conflicting() ? PLAIN_STYLE : empty ? EMPTY_CONFLICT_STYLE : CONFLICT_STYLE);
            column.getChildren().add(line);
        }
        return column;
    }
}
