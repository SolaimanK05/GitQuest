package com.gitquest.ui.sandbox;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.Circle;

/**
 * One file's physics state + visual in {@link CodeGraphView} — position/
 * velocity are mutated every simulation tick. {@code radius} scales with
 * the file's connection count (more-depended-on files read as visually
 * bigger); {@code anchorX}/{@code anchorY} is that file's folder's cluster
 * point, which a gentle per-tick force pulls it toward so files in the
 * same directory drift together.
 */
final class CodeGraphNodeView {

    final String path;
    final String folder;
    final double radius;
    final Circle circle;
    final Label label = new Label();
    double x;
    double y;
    double vx;
    double vy;
    double anchorX;
    double anchorY;

    CodeGraphNodeView(String path, String folder, double radius, boolean hasParseError) {
        this.path = path;
        this.folder = folder;
        this.radius = radius;
        this.circle = new Circle(radius);
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        label.setText(fileName);
        label.setMouseTransparent(true);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #E6E6E6;");
        circle.setStroke(javafx.scene.paint.Color.web("#0C0C0D"));
        circle.setStrokeWidth(1.5);
        Tooltip.install(circle, new Tooltip(path + (hasParseError ? "\n(couldn't be fully parsed)" : "")));
    }
}
