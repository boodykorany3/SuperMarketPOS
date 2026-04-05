package com.pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class CustomerView {

    public static VBox getView() {

        VBox root = new VBox(10);
        root.setStyle("-fx-padding:30;");

        root.getChildren().add(new Label("Customers"));

        for (var entry : CustomerStore.getAll().entrySet()) {
            root.getChildren().add(
                    new Label(entry.getKey() +
                            " - Points: " + entry.getValue())
            );
        }

        return root;
    }
}
