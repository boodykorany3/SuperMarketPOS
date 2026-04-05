package com.pos;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class DashboardController {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final List<String> LOCAL_DATA_FILES = List.of(
            "users.json",
            "products.json",
            "sales.json",
            "customers.json",
            "printer-settings.json"
    );

    @FXML
    private Label roleLabel;
    @FXML
    private Button logoutBtn;
    @FXML
    private Button posBtn;
    @FXML
    private Button inventoryBtn;
    @FXML
    private Button accountsBtn;
    @FXML
    private Button reportsBtn;
    @FXML
    private Button settingsBtn;
    @FXML
    private Button upgradeBtn;
    @FXML
    private StackPane contentArea;

    private static String currentRole;

    public static void setRole(String role) {
        currentRole = role;
    }

    @FXML
    public void initialize() {
        roleLabel.setText(resolveDashboardTitle());
        configureSidebarByRole();

        posBtn.setOnAction(e -> loadPOS());
        inventoryBtn.setOnAction(e -> loadInventory());
        accountsBtn.setOnAction(e -> loadAccounts());
        reportsBtn.setOnAction(e -> showHistory());
        settingsBtn.setOnAction(e -> loadSettings());
        upgradeBtn.setOnAction(e -> openUpgradeCenter());
        logoutBtn.setOnAction(e -> logout());

        Platform.runLater(this::showLowStockAlertIfNeeded);
    }

    private void loadPOS() {
        try {
            Stage stage = (Stage) contentArea.getScene().getWindow();
            FXMLLoader loader = I18n.loader("/view/pos.fxml");
            stage.setScene(new Scene(loader.load()));
            stage.setTitle(I18n.t("window.title.pos"));
            WindowIcon.apply(stage);
            stage.setMaximized(true);
        } catch (Exception e) {
            showMessage(Alert.AlertType.ERROR, I18n.t("dashboard.error.load_pos", e.getMessage()));
        }
    }

    private void loadInventory() {
        try {
            FXMLLoader loader = I18n.loader("/view/inventory.fxml");
            javafx.scene.Parent inventoryView = loader.load();
            contentArea.getChildren().setAll(inventoryView);
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String rootMsg = root.getMessage() == null ? I18n.t("common.no_details") : root.getMessage();
            showMessage(Alert.AlertType.ERROR,
                    I18n.t("dashboard.error.load_inventory",
                            e.getClass().getSimpleName(),
                            root.getClass().getSimpleName(),
                            rootMsg));
        }
    }

    private void loadAccounts() {
        if (!UserSession.isOwner() && !UserSession.isAdmin()) {
            showMessage(Alert.AlertType.WARNING, I18n.t("dashboard.accounts.owner_admin_only"));
            return;
        }

        try {
            FXMLLoader loader = I18n.loader("/view/accounts.fxml");
            javafx.scene.Parent accountsView = loader.load();
            contentArea.getChildren().setAll(accountsView);
        } catch (Exception e) {
            e.printStackTrace();
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String rootMsg = root.getMessage() == null ? I18n.t("common.no_details") : root.getMessage();
            showMessage(Alert.AlertType.ERROR,
                    I18n.t("dashboard.error.load_accounts",
                            e.getClass().getSimpleName(),
                            root.getClass().getSimpleName(),
                            rootMsg));
        }
    }

    private void loadSettings() {
        if (!UserSession.canChangePassword()) {
            showMessage(Alert.AlertType.WARNING, I18n.t("common.not_allowed"));
            return;
        }

        contentArea.getChildren().clear();

        VBox box = new VBox(12);
        box.getStyleClass().add("dash-panel-box");

        Label title = new Label(I18n.t("settings.title"));
        title.getStyleClass().add("dash-panel-title");

        Label info = new Label(I18n.t("settings.security"));
        info.getStyleClass().add("dash-panel-subtitle");

        Button changePasswordBtn = new Button(I18n.t("settings.password.change"));
        changePasswordBtn.getStyleClass().addAll("dash-panel-btn", "dash-panel-btn-primary");
        changePasswordBtn.setOnAction(e -> openChangePasswordDialog());

        box.getChildren().addAll(title, info, changePasswordBtn, new Separator());

        Label languageTitle = new Label(I18n.t("settings.language.title"));
        languageTitle.getStyleClass().add("dash-panel-subtitle");

        Label languageHint = new Label(I18n.t("settings.language.hint"));
        languageHint.getStyleClass().add("dash-panel-note");

        ComboBox<AppLanguage> languageBox = new ComboBox<>(
                FXCollections.observableArrayList(AppLanguage.values())
        );
        languageBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(AppLanguage language) {
                return language == null ? "" : I18n.t(language.getDisplayKey());
            }

            @Override
            public AppLanguage fromString(String string) {
                return null;
            }
        });
        languageBox.setValue(I18n.getLanguage());

        Button applyLanguageBtn = new Button(I18n.t("settings.language.apply"));
        applyLanguageBtn.getStyleClass().addAll("dash-panel-btn", "dash-panel-btn-owner");
        applyLanguageBtn.setOnAction(e -> applyLanguageSelection(languageBox.getValue()));

        box.getChildren().addAll(languageTitle, languageHint, languageBox, applyLanguageBtn);

        if (UserSession.isOwner()) {
            box.getChildren().add(new Separator());

            Label ownerTools = new Label(I18n.t("settings.owner_tools"));
            ownerTools.getStyleClass().add("dash-panel-subtitle");

            Button openUpgradeCenterBtn = new Button(I18n.t("settings.owner_upgrade"));
            openUpgradeCenterBtn.getStyleClass().addAll("dash-panel-btn", "dash-panel-btn-owner");
            openUpgradeCenterBtn.setOnAction(e -> openUpgradeCenter());

            box.getChildren().addAll(ownerTools, openUpgradeCenterBtn);
        }

        contentArea.getChildren().add(box);
    }

    private void applyLanguageSelection(AppLanguage selectedLanguage) {
        if (selectedLanguage == null) {
            return;
        }

        if (selectedLanguage == I18n.getLanguage()) {
            showMessage(Alert.AlertType.INFORMATION, I18n.t("settings.language.already_selected"));
            return;
        }

        I18n.setLanguage(selectedLanguage);
        reloadDashboardScene();
    }

    private void reloadDashboardScene() {
        try {
            Stage stage = (Stage) contentArea.getScene().getWindow();
            FXMLLoader loader = I18n.loader("/view/dashboard.fxml");
            stage.setScene(new Scene(loader.load()));
            stage.setTitle(I18n.t("window.title.dashboard"));
            WindowIcon.apply(stage);
            stage.setMaximized(true);
        } catch (Exception e) {
            showMessage(Alert.AlertType.ERROR, I18n.t("settings.language.reload_failed", e.getMessage()));
        }
    }

    private void openChangePasswordDialog() {
        if (!UserSession.canChangePassword()) {
            showMessage(Alert.AlertType.WARNING, I18n.t("common.not_allowed"));
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.t("settings.password.dialog.title"));

        ButtonType saveType = new ButtonType(I18n.t("settings.password.save"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        PasswordField currentField = new PasswordField();
        currentField.setPromptText(I18n.t("settings.password.current.prompt"));

        PasswordField newField = new PasswordField();
        newField.setPromptText(I18n.t("settings.password.new.prompt"));

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText(I18n.t("settings.password.confirm.prompt"));

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(16));
        grid.add(new Label(I18n.t("settings.password.current.label")), 0, 0);
        grid.add(currentField, 1, 0);
        grid.add(new Label(I18n.t("settings.password.new.label")), 0, 1);
        grid.add(newField, 1, 1);
        grid.add(new Label(I18n.t("settings.password.confirm.label")), 0, 2);
        grid.add(confirmField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveType) {
            return;
        }

        String current = currentField.getText();
        String updated = newField.getText();
        String confirm = confirmField.getText();

        if (updated == null || updated.length() < 4) {
            showMessage(Alert.AlertType.WARNING, I18n.t("settings.password.validation.min_length"));
            return;
        }
        if (!updated.equals(confirm)) {
            showMessage(Alert.AlertType.WARNING, I18n.t("settings.password.validation.mismatch"));
            return;
        }

        boolean changed = UserStore.changePassword(
                UserSession.getUsername(),
                current,
                updated
        );

        if (changed) {
            showMessage(Alert.AlertType.INFORMATION, I18n.t("settings.password.changed"));
        } else {
            showMessage(Alert.AlertType.WARNING, I18n.t("settings.password.wrong_current"));
        }
    }

    private void configureSidebarByRole() {
        boolean canOpenSettings = UserSession.canChangePassword();
        settingsBtn.setVisible(canOpenSettings);
        settingsBtn.setManaged(canOpenSettings);

        boolean canOpenAccounts = UserSession.isOwner() || UserSession.isAdmin();
        accountsBtn.setVisible(canOpenAccounts);
        accountsBtn.setManaged(canOpenAccounts);

        boolean canOpenUpgrade = UserSession.isOwner();
        upgradeBtn.setVisible(canOpenUpgrade);
        upgradeBtn.setManaged(canOpenUpgrade);
    }

    private String resolveDashboardTitle() {
        String branchName = UserSession.getBranchName();
        String branchSuffix = (branchName == null || branchName.isBlank()) ? "" : " - " + branchName;
        if (UserSession.isOwner()) {
            return I18n.t("dashboard.title.owner") + branchSuffix;
        }
        if (UserSession.isAdmin()) {
            return I18n.t("dashboard.title.admin") + branchSuffix;
        }
        if (currentRole != null && !currentRole.isBlank()) {
            return I18n.t("dashboard.title.logged_as", localizeRole(currentRole)) + branchSuffix;
        }
        return I18n.t("dashboard.title.default") + branchSuffix;
    }

    private void openUpgradeCenter() {
        if (!UserSession.isOwner()) {
            showMessage(Alert.AlertType.WARNING, I18n.t("dashboard.owner_only"));
            return;
        }

        contentArea.getChildren().clear();

        VBox box = new VBox(12);
        box.getStyleClass().add("dash-panel-box");

        Label title = new Label(I18n.t("upgrade.title"));
        title.getStyleClass().add("dash-panel-title");

        Label info = new Label(I18n.t("upgrade.description"));
        info.getStyleClass().add("dash-panel-note");
        Label schedule = new Label(I18n.t("upgrade.schedule"));
        schedule.getStyleClass().add("dash-panel-note");

        Button backupBtn = new Button(I18n.t("upgrade.backup_now"));
        backupBtn.getStyleClass().addAll("dash-panel-btn", "dash-panel-btn-primary");
        backupBtn.setOnAction(e -> backupLocalData());

        Button checklistBtn = new Button(I18n.t("upgrade.show_checklist"));
        checklistBtn.getStyleClass().addAll("dash-panel-btn", "dash-panel-btn-owner");
        checklistBtn.setOnAction(e -> showUpgradeChecklist());

        box.getChildren().addAll(title, info, schedule, backupBtn, checklistBtn);
        contentArea.getChildren().add(box);
    }

    private void showUpgradeChecklist() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(I18n.t("upgrade.checklist.title"));
        alert.setHeaderText(I18n.t("upgrade.checklist.header"));
        alert.setContentText(I18n.t("upgrade.checklist.items"));
        alert.show();
    }

    private void backupLocalData() {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backupDir = Paths.get("backups", "backup-" + stamp);

        try {
            Files.createDirectories(backupDir);
            int copied = 0;

            for (String fileName : LOCAL_DATA_FILES) {
                Path source = Paths.get(fileName);
                if (!Files.exists(source)) {
                    continue;
                }
                Files.copy(
                        source,
                        backupDir.resolve(source.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING
                );
                copied++;
            }

            if (copied == 0) {
                showMessage(Alert.AlertType.WARNING, I18n.t("upgrade.backup.no_files"));
                return;
            }

            showMessage(Alert.AlertType.INFORMATION,
                    I18n.t("upgrade.backup.success", backupDir.toAbsolutePath()));
        } catch (Exception e) {
            showMessage(Alert.AlertType.ERROR, I18n.t("upgrade.backup.failed", e.getMessage()));
        }
    }

    private void showLowStockAlertIfNeeded() {
        List<Product> lowStock = InventoryStore.getLowStockProducts(LOW_STOCK_THRESHOLD);
        if (lowStock.isEmpty()) {
            return;
        }

        StringBuilder details = new StringBuilder();
        int previewLimit = Math.min(lowStock.size(), 10);
        for (int i = 0; i < previewLimit; i++) {
            Product product = lowStock.get(i);
            details.append(I18n.t("dashboard.low_stock.row", product.getName(), product.getQuantity())).append("\n");
        }
        if (lowStock.size() > previewLimit) {
            details.append(I18n.t("dashboard.low_stock.more_items", lowStock.size() - previewLimit));
        }

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(I18n.t("dashboard.low_stock.title"));
        alert.setHeaderText(I18n.t("dashboard.low_stock.header", lowStock.size()));
        alert.setContentText(details.toString());

        ButtonType openInventory = new ButtonType(I18n.t("dashboard.low_stock.open_inventory"));
        ButtonType later = new ButtonType(I18n.t("dashboard.low_stock.later"), ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(openInventory, later);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == openInventory) {
            loadInventory();
        }
    }

    private void showMessage(Alert.AlertType type, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(resolveAlertTitle(type));
        alert.setHeaderText(message);
        alert.show();
    }

    private String resolveAlertTitle(Alert.AlertType type) {
        if (type == Alert.AlertType.ERROR) {
            return I18n.t("alert.title.error");
        }
        if (type == Alert.AlertType.WARNING) {
            return I18n.t("alert.title.warning");
        }
        return I18n.t("alert.title.info");
    }

    private void logout() {
        try {
            UserSession.clear();
            Stage stage = (Stage) logoutBtn.getScene().getWindow();
            FXMLLoader loader = I18n.loader("/view/login.fxml");
            stage.setScene(new Scene(loader.load()));
            stage.setTitle(I18n.t("window.title.login"));
            WindowIcon.apply(stage);
            stage.setMaximized(true);
        } catch (Exception e) {
            showMessage(Alert.AlertType.ERROR, I18n.t("dashboard.error.logout", e.getMessage()));
        }
    }

    private void showHistory() {
        contentArea.getChildren().clear();
        contentArea.getChildren().add(SalesHistoryView.getView());
    }

    private String localizeRole(String role) {
        if ("OWNER".equalsIgnoreCase(role)) {
            return I18n.t("role.owner");
        }
        if ("ADMIN".equalsIgnoreCase(role)) {
            return I18n.t("role.admin");
        }
        if ("CASHIER".equalsIgnoreCase(role)) {
            return I18n.t("role.cashier");
        }
        return role == null ? "" : role;
    }
}
