package com.pos;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.net.URL;

public final class WindowIcon {
    private static final String ICON_RESOURCE = "/images/abusamir-logo-circle.png";

    private WindowIcon() {
    }

    public static void apply(Stage stage) {
        if (stage == null || !stage.getIcons().isEmpty()) {
            return;
        }
        Image icon = loadIcon();
        if (icon != null) {
            stage.getIcons().add(icon);
        }
    }

    private static Image loadIcon() {
        URL url = WindowIcon.class.getResource(ICON_RESOURCE);
        if (url == null) {
            return null;
        }
        return new Image(url.toExternalForm());
    }
}
