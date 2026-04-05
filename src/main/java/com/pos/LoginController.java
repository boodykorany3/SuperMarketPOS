package com.pos;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField visiblePasswordField;
    @FXML
    private CheckBox showPasswordToggle;
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;
    @FXML
    private ProgressIndicator loginProgress;
    @FXML
    private VBox loginCard;
    @FXML
    private VBox brandPane;

    private List<User> users = new ArrayList<>();
    private boolean authenticating = false;

    @FXML
    public void initialize() {
        if (!BackendMode.useApiSync()) {
            try {
                users = UserStore.getUsers();
            } catch (Exception e) {
                errorLabel.setText(I18n.t("login.error.db_unavailable"));
                loginButton.setDisable(true);
                return;
            }

            if (users.isEmpty()) {
                errorLabel.setText(I18n.t("login.error.no_users"));
                loginButton.setDisable(true);
                return;
            }
        }

        wirePasswordVisibility();
        loginButton.setOnAction(e -> login());
        usernameField.setOnAction(e -> passwordField.requestFocus());
        passwordField.setOnAction(e -> login());
        visiblePasswordField.setOnAction(e -> login());

        usernameField.textProperty().addListener((obs, oldVal, newVal) -> clearError());
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> clearError());
        updateLoginButtonState();
        usernameField.textProperty().addListener((obs, oldVal, newVal) -> updateLoginButtonState());
        passwordField.textProperty().addListener((obs, oldVal, newVal) -> updateLoginButtonState());

        loginCard.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            updateResponsiveLayout(newScene.getWidth());
            newScene.widthProperty().addListener((wObs, oldWidth, newWidth) ->
                    updateResponsiveLayout(newWidth.doubleValue()));
        });

        Platform.runLater(() -> {
            usernameField.requestFocus();
            if (loginCard.getScene() != null) {
                updateResponsiveLayout(loginCard.getScene().getWidth());
            }
        });

        usernameField.requestFocus();
        playIntroAnimation();
    }

    private void login() {
        if (authenticating) {
            return;
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isBlank() || password.isBlank()) {
            errorLabel.setText(I18n.t("login.error.enter_credentials"));
            return;
        }

        setAuthenticating(true);
        PauseTransition delay = new PauseTransition(Duration.millis(220));
        delay.setOnFinished(evt -> {
            try {
                User authenticated = UserStore.login(username, password);
                if (authenticated != null) {
                    openByRole(authenticated);
                    return;
                }

                errorLabel.setText(I18n.t("login.error.invalid_credentials"));
            } catch (Exception ex) {
                errorLabel.setText(I18n.t("login.error.api_unreachable"));
            }
            setAuthenticating(false);
        });
        delay.play();
    }

    private void clearError() {
        if (errorLabel.getText() != null && !errorLabel.getText().isBlank()) {
            errorLabel.setText("");
        }
    }

    private void wirePasswordVisibility() {
        visiblePasswordField.setManaged(false);
        visiblePasswordField.setVisible(false);
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());

        showPasswordToggle.selectedProperty().addListener((obs, oldVal, show) -> {
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
            visiblePasswordField.setVisible(show);
            visiblePasswordField.setManaged(show);
            if (show) {
                visiblePasswordField.requestFocus();
                visiblePasswordField.positionCaret(visiblePasswordField.getText().length());
            } else {
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
            }
        });
    }

    private void setAuthenticating(boolean value) {
        authenticating = value;
        loginProgress.setManaged(value);
        loginProgress.setVisible(value);
        usernameField.setDisable(value);
        passwordField.setDisable(value);
        visiblePasswordField.setDisable(value);
        showPasswordToggle.setDisable(value);
        loginButton.setText(value ? I18n.t("login.button.signing_in") : I18n.t("login.button.sign_in"));
        updateLoginButtonState();
    }

    private void updateLoginButtonState() {
        boolean invalidFields = usernameField.getText().trim().isBlank() || passwordField.getText().isBlank();
        boolean usersUnavailable = !BackendMode.useApiSync() && users.isEmpty();
        loginButton.setDisable(authenticating || usersUnavailable || invalidFields);
    }

    private void updateResponsiveLayout(double width) {
        boolean compact = width < 920;
        brandPane.setVisible(!compact);
        brandPane.setManaged(!compact);
        if (compact) {
            if (!loginCard.getStyleClass().contains("login-card-compact")) {
                loginCard.getStyleClass().add("login-card-compact");
            }
            return;
        }
        loginCard.getStyleClass().remove("login-card-compact");
    }

    private void playIntroAnimation() {
        if (loginCard == null) {
            return;
        }

        loginCard.setOpacity(0);
        loginCard.setTranslateY(16);

        FadeTransition fade = new FadeTransition(Duration.millis(420), loginCard);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(420), loginCard);
        slide.setFromY(16);
        slide.setToY(0);

        fade.play();
        slide.play();
    }

    private void openByRole(User user) {
        try {
            if (BackendMode.useApiSync()) {
                InventoryStore.reload();
            }

            FXMLLoader loader;
            UserSession.setRole(user.getRole());
            UserSession.setUsername(user.getUsername());
            if (user.getBranchId() != null) {
                UserSession.setBranchId(user.getBranchId());
                UserSession.setBranchCode(user.getBranchCode());
                UserSession.setBranchName(user.getBranchName());
            }

            String stageTitle;
            if ("CASHIER".equals(user.getRole())) {
                loader = I18n.loader("/view/pos.fxml");
                stageTitle = I18n.t("window.title.pos");
            } else {
                DashboardController.setRole(user.getRole());
                loader = I18n.loader("/view/dashboard.fxml");
                stageTitle = I18n.t("window.title.logged_role", user.getRole());
            }

            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle(stageTitle);
            WindowIcon.apply(stage);
            stage.setMaximized(true);
        } catch (Exception e) {
            e.printStackTrace();
            errorLabel.setText(I18n.t("login.error.open_next_screen"));
            setAuthenticating(false);
        }
    }
}
