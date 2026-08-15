package com.gitquest.ui.common;

import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

public final class TooltipHelper {

    private TooltipHelper() {
    }

    public static void install(Control control, String text) {
        Tooltip.install(control, new Tooltip(text));
    }
}
