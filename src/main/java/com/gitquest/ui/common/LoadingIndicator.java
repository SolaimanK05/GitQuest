package com.gitquest.ui.common;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.util.Duration;

/**
 * A small spinning-arc indicator for "something is happening" states that used to be disable-only
 * (clone/open/init a repo, dispatch a git command, wait on a Gemini reply, analyze the code
 * graph) with zero visual feedback beyond a grayed-out button. Hidden and stopped by default;
 * {@link #setActive(boolean)} is the only thing callers need to touch — it starts/stops the spin
 * and shows/hides itself together, so a controller's existing {@code setBusy(boolean)} method just
 * gets one extra line rather than a parallel state machine to keep in sync.
 */
public final class LoadingIndicator extends StackPane {

    private final RotateTransition spin;

    public LoadingIndicator() {
        Arc arc = new Arc(9, 9, 7, 7, 0, 300);
        arc.setType(ArcType.OPEN);
        arc.setFill(Color.TRANSPARENT);
        arc.setStroke(Color.web("#F05133"));
        arc.setStrokeWidth(2);
        arc.setStrokeLineCap(StrokeLineCap.ROUND);
        getChildren().add(arc);

        setPrefSize(18, 18);
        setMinSize(18, 18);
        setMaxSize(18, 18);
        setMouseTransparent(true);

        spin = new RotateTransition(Duration.millis(800), arc);
        spin.setByAngle(360);
        spin.setCycleCount(Animation.INDEFINITE);
        spin.setInterpolator(Interpolator.LINEAR);

        managedProperty().bind(visibleProperty());
        setVisible(false);
    }

    public void setActive(boolean active) {
        setVisible(active);
        if (active) {
            spin.play();
        } else {
            spin.stop();
        }
    }
}
