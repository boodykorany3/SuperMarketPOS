package com.pos;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class OwnerPanel {

    public static void open() {

        Stage stage = new Stage();

        VBox root = new VBox(20);
        root.setStyle("-fx-padding:30;");

        Label title = new Label("OWNER PANEL");
        title.setStyle("-fx-font-size:24; -fx-font-weight:bold;");

        Label info = new Label("Welcome Owner");

        root.getChildren().addAll(title, info);

        stage.setScene(new Scene(root, 400, 300));
        stage.setTitle("Owner Panel");
        WindowIcon.apply(stage);
        stage.setMaximized(true);
        stage.show();
    }
}
