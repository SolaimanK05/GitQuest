package com.gitquest.ui.sandbox;

import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.Circle;

/** One file's physics state + visual in {@link CodeGraphView} — position/velocity are mutated every simulation tick. */
final class CodeGraphNodeView {

    static final double RADIUS = 9;

    final String path;
    final Circle circle = new Circle(RADIUS);
    final Label label = new Label();
    double x;
    double y;
    double vx;
    double vy;

    CodeGraphNodeView(String path, boolean hasParseError) {
        this.path = path;
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        label.setText(fileName);
        label.setMouseTransparent(true);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #E6E6E6;");
        circle.setStroke(javafx.scene.paint.Color.web("#0C0C0D"));
        circle.setStrokeWidth(1.5);
        Tooltip.install(circle, new Tooltip(path + (hasParseError ? "\n(couldn't be fully parsed)" : "")));
    }
}
