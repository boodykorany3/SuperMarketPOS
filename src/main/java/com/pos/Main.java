package com.pos;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        if (!BackendMode.useApiSync()) {
            Database.initialize();
            if (!Database.isAvailable() && !Database.isLocalFallbackEnabled()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Database Error");
                alert.setHeaderText("Could not connect to MySQL database");
                alert.setContentText("Start MySQL and configure POS_DB_URL / POS_DB_USER / POS_DB_PASS, then restart the app.");
                alert.showAndWait();
                Platform.exit();
                return;
            }
            InventoryStore.reload();
            SalesStore.reload();
        }

        FXMLLoader loader = I18n.loader("/view/login.fxml");

        Scene scene = new Scene(loader.load());

        stage.setScene(scene);
        stage.setTitle(I18n.t("window.title.login"));
        WindowIcon.apply(stage);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
