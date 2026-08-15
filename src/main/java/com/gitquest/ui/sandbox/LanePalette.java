package com.gitquest.ui.sandbox;

import javafx.scene.paint.Color;

/**
 * Categorical colors cycled per lane, so branches are visually distinct
 * from one another. Deliberately excludes Git's brand red — that color is
 * reserved for the HEAD ring and other accent chrome (see dark-theme.css),
 * so it never collides with a lane's color.
 */
public final class LanePalette {

    private static final Color[] COLORS = {
            Color.web("#4C8BF5"), // blue
            Color.web("#3DDC97"), // teal-green
            Color.web("#B48CFF"), // purple
            Color.web("#FFC857"), // amber
            Color.web("#5BD1D7"), // cyan
            Color.web("#FF8FA3"), // pink
    };

    private LanePalette() {
    }

    public static Color forLane(int lane) {
        int index = Math.floorMod(lane, COLORS.length);
        return COLORS[index];
    }
}
