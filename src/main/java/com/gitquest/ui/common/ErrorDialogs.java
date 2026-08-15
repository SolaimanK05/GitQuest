package com.gitquest.ui.common;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

public final class ErrorDialogs {

    private ErrorDialogs() {
    }

    public static void show(String title, Throwable error) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(rootMessage(error));
        alert.showAndWait();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : cause.toString();
    }
}
