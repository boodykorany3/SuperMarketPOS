package com.pos;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class POSController {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("hh:mm:ss a");
    private static final DateTimeFormatter RECEIPT_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");

    @FXML private VBox invoiceContainer;
    @FXML private Label totalLabel;
    @FXML private Label changeLabel;
    @FXML private TextField paidField;
    @FXML private TextField barcodeField;
    @FXML private Label timeLabel;
    @FXML private Label invoiceLabel;
    @FXML private Button btnPay;
    @FXML private Button btnDelete;
    @FXML private Button btnCancel;
    @FXML private Label customerPointsLabel;

    private double total = 0;
    private HBox selectedRow = null;

    private Map<HBox, Double> priceMap = new HashMap<>();
    private final Map<HBox, String> stockBarcodeMap = new HashMap<>();
    @FXML private TextField phoneField;
    private PrinterSettings printerSettings;
    private ReceiptPrinterService printerService;

    @FXML
    public void initialize() {
        printerSettings = PrinterSettingsStore.load();
        printerService = PrinterServiceFactory.create(printerSettings);

        addTopBar();
        startClock();
        generateInvoiceNumber();

        barcodeField.setOnAction(e -> handleBarcode());
        barcodeField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB) {
                handleBarcode();
                e.consume();
            }
        });
        paidField.textProperty().addListener((obs, o, n) -> calculateChange());
        phoneField.textProperty().addListener((obs,o,n)->{
            int p = CustomerStore.getPoints(n);
            customerPointsLabel.setText("Points: " + p);
        });

        btnPay.setOnAction(e -> processPayment());
        btnDelete.setOnAction(e -> deleteSelected());
        btnCancel.setOnAction(e -> clearAll());

        invoiceContainer.sceneProperty().addListener((obs, o, scene) -> {
            if (scene != null) {
                scene.setOnKeyPressed(e -> {
                    if (e.getCode() == KeyCode.DELETE) deleteSelected();
                    if (e.getCode() == KeyCode.ESCAPE) clearAll();
                });
            }
        });
    }

    // ================= PAYMENT =================
    private void processPayment() {

        if (total == 0) {
            showAlert("No items in invoice");
            return;
        }

        Double paid = PosPaymentService.parsePaidAmount(paidField.getText());
        if (paid == null) {
            showAlert("Enter valid paid amount");
            return;
        }

        double originalTotal = total;
        LoyaltyDecision loyaltyDecision = prepareLoyaltyDecision();

        if (!PosPaymentService.isEnoughPaid(paid, total)) {
            rollbackDiscount(originalTotal, loyaltyDecision);
            showAlert("Paid amount is not enough!");
            return;
        }

        Sale sale = buildSale();
        SalesStore.AddSaleResult saveResult = SalesStore.addSale(sale);
        if (saveResult != SalesStore.AddSaleResult.SUCCESS) {
            rollbackDiscount(originalTotal, loyaltyDecision);
            if (saveResult == SalesStore.AddSaleResult.OUT_OF_STOCK) {
                InventoryStore.reload();
                showAlert("Stock changed on another device. Please rescan the invoice.");
            } else {
                showAlert("Could not save sale. Please try again.");
            }
            return;
        }

        InventoryStore.reload();
        applyLoyaltyDecision(loyaltyDecision);
        tryPrintReceipt(sale);
        showInvoiceWindow(sale);
    }
    private Sale buildSale() {

        Map<String, Integer> items = new java.util.HashMap<>();
        Map<String, Integer> itemBarcodes = new java.util.HashMap<>();
        Map<String, String> itemNamesByBarcode = new java.util.HashMap<>();

        for (var node : invoiceContainer.getChildren()) {

            HBox row = (HBox) node;

            Label name = (Label) row.getChildren().get(0);
            Spinner<Integer> spinner = getRowSpinner(row);
            String barcode = stockBarcodeMap.getOrDefault(row, (String) row.getUserData());
            int quantity = spinner.getValue();

            items.merge(name.getText(), quantity, Integer::sum);
            if (barcode != null && !barcode.isBlank()) {
                itemBarcodes.merge(barcode, quantity, Integer::sum);
                itemNamesByBarcode.putIfAbsent(barcode, name.getText());
            }
        }

        return new Sale(
                invoiceLabel.getText(),
                java.time.LocalDateTime.now(),
                items,
                itemBarcodes,
                itemNamesByBarcode,
                total
        );
    }


    // ================= BARCODE =================
    private void handleBarcode() {
        PosBarcodeService.LookupResult lookupResult = PosBarcodeService.resolve(barcodeField.getText());
        if (!lookupResult.isSuccess()) {
            if (lookupResult.failureReason() == PosBarcodeService.FailureReason.EMPTY) {
                return;
            }
            if (lookupResult.failureReason() == PosBarcodeService.FailureReason.WEIGHTED_PRODUCT_CODE_NOT_FOUND) {
                showAlert("Weighted barcode product code not found: " + lookupResult.details());
            } else {
                showAlert("Product Not Found");
            }
            barcodeField.clear();
            return;
        }

        PosBarcodeService.ScanResult scan = lookupResult.scan();
        Product product = scan.product();
        if (scan.weighted()) {
            addWeightedProduct(scan);
            barcodeField.clear();
            return;
        }

        for (var node : invoiceContainer.getChildren()) {
            HBox row = (HBox) node;
            String rowBarcode = (String) row.getUserData();

            if (product.getBarcode().equals(rowBarcode)) {
                Spinner<Integer> spinner = getRowSpinner(row);
                if (spinner.getValue() >= product.getQuantity()) {
                    showAlert("Out of Stock!");
                    barcodeField.clear();
                    return;
                }
                spinner.getValueFactory()
                        .setValue(spinner.getValue() + 1);

                barcodeField.clear();
                return;
            }
        }

        if (product.getQuantity() <= 0) {
            showAlert("Out of Stock!");
            barcodeField.clear();
            return;
        }

        addProduct(product);
        barcodeField.clear();
    }

    // ================= ADD PRODUCT =================
    private void addProduct(Product product) {

        Label nameLabel = new Label(product.getName());
        nameLabel.getStyleClass().add("row-name");

        Spinner<Integer> qtySpinner =
                new Spinner<>(1, 999, 1);
        qtySpinner.setEditable(true);

        Label totalLabelRow = new Label();
        totalLabelRow.getStyleClass().add("row-total");

        HBox row = new HBox(78, nameLabel, qtySpinner, totalLabelRow);
        row.getStyleClass().add("invoice-row");
        row.setUserData(product.getBarcode());

        priceMap.put(row, product.getPrice());
        stockBarcodeMap.put(row, product.getBarcode());

        row.setOnMouseClicked(e -> {
            if (selectedRow != null)
                selectedRow.getStyleClass().remove("selected-row");
            selectedRow = row;
            row.getStyleClass().add("selected-row");
        });

        qtySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            int previous = oldVal == null ? 1 : oldVal;
            int requested = newVal == null ? 1 : newVal;

            if (requested > product.getQuantity()) {
                showAlert("Not enough stock!");
                qtySpinner.getValueFactory().setValue(previous);
                return;
            }

            updateRowTotal(row);
        });

        invoiceContainer.getChildren().add(row);
        updateRowTotal(row);
    }

    private void addWeightedProduct(PosBarcodeService.ScanResult scan) {
        Product product = scan.product();
        if (product.getQuantity() <= 0) {
            showAlert("Out of Stock!");
            return;
        }

        double priceForOnePack = scan.unitPrice();
        String displayName = scan.displayName();
        Label nameLabel = new Label(displayName);
        nameLabel.getStyleClass().add("row-name");

        Spinner<Integer> qtySpinner = new Spinner<>(1, 999, 1);
        qtySpinner.setEditable(true);

        Label totalLabelRow = new Label();
        totalLabelRow.getStyleClass().add("row-total");

        HBox row = new HBox(78, nameLabel, qtySpinner, totalLabelRow);
        row.getStyleClass().add("invoice-row");
        row.setUserData(scan.scannedBarcode());

        priceMap.put(row, priceForOnePack);
        stockBarcodeMap.put(row, scan.stockBarcode());

        row.setOnMouseClicked(e -> {
            if (selectedRow != null) {
                selectedRow.getStyleClass().remove("selected-row");
            }
            selectedRow = row;
            row.getStyleClass().add("selected-row");
        });

        qtySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            int previous = oldVal == null ? 1 : oldVal;
            int requested = newVal == null ? 1 : newVal;

            if (requested > product.getQuantity()) {
                showAlert("Not enough stock!");
                qtySpinner.getValueFactory().setValue(previous);
                return;
            }

            updateRowTotal(row);
        });

        invoiceContainer.getChildren().add(row);
        updateRowTotal(row);
    }

    // ================= UPDATE TOTAL =================
    private void updateRowTotal(HBox row) {

        Spinner<Integer> spinner = getRowSpinner(row);

        Label totalLabelRow =
                (Label) row.getChildren().get(2);

        double price = priceMap.get(row);
        double rowTotal = PosInvoiceService.calculateRowTotal(spinner.getValue(), price);

        totalLabelRow.setText(String.format("%.2f", rowTotal));
        recalcTotal();
    }

    @SuppressWarnings("unchecked")
    private Spinner<Integer> getRowSpinner(HBox row) {
        return (Spinner<Integer>) row.getChildren().get(1);
    }

    // ================= DELETE =================
    private void deleteSelected() {

        if (selectedRow == null) return;

        invoiceContainer.getChildren().remove(selectedRow);
        priceMap.remove(selectedRow);
        stockBarcodeMap.remove(selectedRow);
        selectedRow = null;

        recalcTotal();
    }

    // ================= CLEAR =================
    private void clearAll() {
        invoiceContainer.getChildren().clear();
        priceMap.clear();
        stockBarcodeMap.clear();

        total = 0;
        updateTotal();
        paidField.clear();
        changeLabel.setText("0.00");
    }

    private void clearPaidInvoice() {
        invoiceContainer.getChildren().clear();
        priceMap.clear();
        stockBarcodeMap.clear();
        selectedRow = null;
        total = 0;
        updateTotal();
        barcodeField.clear();
        paidField.clear();
        phoneField.clear();
        customerPointsLabel.setText("Points: 0");
        changeLabel.setText("0.00");
    }

    // ================= TOTAL =================
    private void recalcTotal() {
        total = 0;
        for (var node : invoiceContainer.getChildren()) {
            HBox row = (HBox) node;
            Label lbl = (Label) row.getChildren().get(2);
            total += Double.parseDouble(lbl.getText());
        }
        updateTotal();
        calculateChange();
    }

    private void updateTotal() {
        totalLabel.setText(String.format("%.2f", total));
    }

    private void calculateChange() {
        double change = PosInvoiceService.calculateChange(total, paidField.getText());
        changeLabel.setText(String.format("%.2f", change));
    }

    // ================= RECEIPT UI =================
    private void showInvoiceWindow(Sale sale) {

        Stage invoiceStage = new Stage();
        WindowIcon.apply(invoiceStage);

        VBox root = new VBox(8);
        root.setStyle(
                "-fx-background-color:white;" +
                        "-fx-padding:20;" +
                        "-fx-font-family:'Consolas';"
        );
        root.setPrefWidth(320);
        root.setFillWidth(true);

        HBox logoRow = new HBox(buildReceiptLogo());
        logoRow.setAlignment(Pos.CENTER);

        Label store = new Label("ABU SAMIR MARKET");
        store.setMaxWidth(Double.MAX_VALUE);
        store.setAlignment(Pos.CENTER);
        store.setStyle("-fx-font-size:16; -fx-font-weight:bold;");

        Label address = new Label("Cairo - Egypt");
        address.setStyle("-fx-font-size:11;");

        Label invoiceNo = new Label(sale.getInvoiceNumber());
        String formattedReceiptTime = sale.getDateTime() == null
                ? "-"
                : sale.getDateTime().format(RECEIPT_DATE_TIME_FORMAT);
        Label dateTime = new Label("Time: " + formattedReceiptTime);

        Separator sep1 = new Separator();

        VBox itemsBox = new VBox(4);

        Label headerLine = new Label(
                String.format("%-12s %4s %7s",
                        "ITEM", "QTY", "TOTAL")
        );
        headerLine.setStyle("-fx-font-weight:bold;");

        itemsBox.getChildren().add(headerLine);
        itemsBox.getChildren().add(new Separator());

        for (var node : invoiceContainer.getChildren()) {

            HBox row = (HBox) node;

            Label name = (Label) row.getChildren().get(0);
            Spinner<Integer> spinner = getRowSpinner(row);
            Label totalRow =
                    (Label) row.getChildren().get(2);

            String line = String.format(
                    "%-12s %4s %7s",
                    name.getText().length() > 12
                            ? name.getText().substring(0, 12)
                            : name.getText(),
                    spinner.getValue(),
                    totalRow.getText()
            );

            itemsBox.getChildren().add(new Label(line));
        }

        Separator sep2 = new Separator();

        Label totalLine = new Label(
                String.format("TOTAL : %.2f", total)
        );
        totalLine.setStyle("-fx-font-size:14; -fx-font-weight:bold;");

        Label paidLine = new Label(
                String.format("PAID  : %s", paidField.getText())
        );

        Label changeLine = new Label(
                String.format("CHANGE: %s", changeLabel.getText())
        );

        Separator sep3 = new Separator();

        Label thanks = new Label("Thank You For Shopping!");
        thanks.setStyle("-fx-font-size:11;");

        Button closeBtn = new Button("Close");
        closeBtn.setMaxWidth(Double.MAX_VALUE);
        closeBtn.setStyle(
                "-fx-background-color:#0f172a;" +
                        "-fx-text-fill:white;"
        );
        if (!phoneField.getText().isEmpty()) {

            root.getChildren().add(
                    new Label("Earned Points: " + earnedPoints)
            );

            root.getChildren().add(
                    new Label("Total Points: " +
                            CustomerStore.getPoints(phoneField.getText()))
            );
        }

        closeBtn.setOnAction(e -> {
            invoiceStage.close();
            clearPaidInvoice();
            generateInvoiceNumber();
        });

        root.getChildren().addAll(
                logoRow,
                store,
                address,
                invoiceNo,
                dateTime,
                sep1,
                itemsBox,
                sep2,
                totalLine,
                paidLine,
                changeLine,
                sep3,
                thanks,
                closeBtn
        );

        invoiceStage.setScene(new Scene(root));
        invoiceStage.setTitle("Receipt");
        invoiceStage.show();
    }

    private void tryPrintReceipt(Sale sale) {
        try {
            printerService.print(sale, paidField.getText(), changeLabel.getText());
        } catch (Exception e) {
            showAlert("Saved, but thermal print failed: " + e.getMessage());
        }
    }

    // ================= CLOCK =================
    private void startClock() {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1), e ->
                        timeLabel.setText(
                                LocalTime.now()
                                        .format(CLOCK_FORMAT)
                        )
                )
        );
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private void generateInvoiceNumber() {
        invoiceLabel.setText("Invoice #AUTO");
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(msg);
        alert.show();
    }
    // ================= TOP BAR =================
    private void addTopBar() {

        if (invoiceContainer.getScene() == null) {
            invoiceContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
                if (newScene != null) createTopBar();
            });
        } else {
            createTopBar();
        }
    }

    private void createTopBar() {

        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(10);
        bar.getStyleClass().add("pos-super-bar");

        javafx.scene.control.Label title =
                new javafx.scene.control.Label("POS SYSTEM");
        title.getStyleClass().add("pos-super-title");

        javafx.scene.layout.Pane spacer =
                new javafx.scene.layout.Pane();
        javafx.scene.layout.HBox.setHgrow(
                spacer,
                javafx.scene.layout.Priority.ALWAYS
        );

        javafx.scene.control.Button settings =
                new javafx.scene.control.Button("Settings");
        settings.getStyleClass().add("pos-super-btn");

        javafx.scene.control.Button owner =
                new javafx.scene.control.Button("Owner Panel");
        owner.getStyleClass().add("pos-super-btn");

        boolean canAccessSettings = canAccessSettings();
        boolean canAccessOwnerPanel = canAccessOwnerPanel();
        settings.setManaged(canAccessSettings);
        settings.setVisible(canAccessSettings);
        owner.setManaged(canAccessOwnerPanel);
        owner.setVisible(canAccessOwnerPanel);

        settings.setOnAction(e -> openSettings());
        owner.setOnAction(e -> openOwnerPanel());

        bar.getChildren().addAll(title, spacer, settings, owner);

        VBox root =
                (VBox) invoiceContainer.getScene().getRoot();

        root.getChildren().add(0, bar);
    }
    private boolean checkOwnerPassword() {

        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter Owner Password");

        var result = dialog.showAndWait();

        return result.isPresent() && isValidOwnerPassword(result.get());
    }

    private void openSettings() {
        if (!canAccessSettings()) {
            showAlert("Not allowed");
            return;
        }

        if (!checkOwnerPassword()) {
            showAlert("Wrong password!");
            return;
        }
        configurePrinter();
    }

    private void configurePrinter() {
        ChoiceDialog<String> modeDialog = new ChoiceDialog<>("Disabled", "Disabled", "LAN", "Windows Printer");
        modeDialog.setHeaderText("Thermal Printer Mode");
        modeDialog.setContentText("Choose mode:");
        Optional<String> modeResult = modeDialog.showAndWait();
        if (modeResult.isEmpty()) {
            return;
        }

        String mode = modeResult.get();
        if ("Disabled".equals(mode)) {
            printerSettings.setMode(PrinterSettings.Mode.NONE);
            PrinterSettingsStore.save(printerSettings);
            printerService = PrinterServiceFactory.create(printerSettings);
            showAlert("Printer disabled");
            return;
        }

        if ("LAN".equals(mode)) {
            if (!configureLanPrinter()) {
                return;
            }
        } else {
            if (!configureWindowsPrinter()) {
                return;
            }
        }

        printerService = PrinterServiceFactory.create(printerSettings);
        PrinterSettingsStore.save(printerSettings);
        showAlert("Printer settings saved");
        testPrinter();
    }

    private boolean configureLanPrinter() {
        TextInputDialog ipDialog = new TextInputDialog(printerSettings.getLanHost().isBlank() ? "192.168.1.100" : printerSettings.getLanHost());
        ipDialog.setHeaderText("Enter printer IP");
        Optional<String> ipResult = ipDialog.showAndWait();
        if (ipResult.isEmpty() || ipResult.get().isBlank()) {
            return false;
        }

        TextInputDialog portDialog = new TextInputDialog(String.valueOf(printerSettings.getLanPort()));
        portDialog.setHeaderText("Enter printer port (default 9100)");
        Optional<String> portResult = portDialog.showAndWait();
        if (portResult.isEmpty()) {
            return false;
        }

        int port;
        try {
            port = Integer.parseInt(portResult.get().trim());
        } catch (Exception e) {
            showAlert("Invalid port");
            return false;
        }
        if (port < 1 || port > 65535) {
            showAlert("Port must be between 1 and 65535");
            return false;
        }

        printerSettings.setMode(PrinterSettings.Mode.LAN);
        printerSettings.setLanHost(ipResult.get().trim());
        printerSettings.setLanPort(port);
        return true;
    }

    private boolean configureWindowsPrinter() {
        TextInputDialog nameDialog = new TextInputDialog(printerSettings.getWindowsPrinterName());
        nameDialog.setHeaderText("Enter exact Windows printer name");
        Optional<String> nameResult = nameDialog.showAndWait();
        if (nameResult.isEmpty() || nameResult.get().isBlank()) {
            return false;
        }

        printerSettings.setMode(PrinterSettings.Mode.WINDOWS);
        printerSettings.setWindowsPrinterName(nameResult.get().trim());
        return true;
    }

    private void testPrinter() {
          try {
            Map<String, Integer> items = new HashMap<>();
            items.put("TEST", 1);
            Sale testSale = new Sale("TEST-PRINT", java.time.LocalDateTime.now(), items, 0.0);
            printerService.print(testSale, "0.00", "0.00");
            showAlert("Test print sent");
        } catch (Exception e) {
            showAlert("Test print failed: " + e.getMessage());
        }
    }

    private void openOwnerPanel() {
        if (!canAccessOwnerPanel()) {
            showAlert("Not allowed");
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter Owner Password");

        var result = dialog.showAndWait();

        // غير الباسورد براحتك
        if (result.isEmpty() || !isValidOwnerPassword(result.get())) {
            showAlert("Wrong password!");
            return;
        }

        try {
            UserSession.setRole("OWNER");
            UserSession.setUsername(findOwnerUsername());
            DashboardController.setRole("OWNER");

            javafx.fxml.FXMLLoader loader = I18n.loader("/view/dashboard.fxml");
            javafx.scene.Parent root = loader.load();

            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle(I18n.t("window.title.owner_dashboard"));
            WindowIcon.apply(stage);
            stage.setMaximized(true);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean canAccessSettings() {
        return UserSession.isOwner() || UserSession.isAdmin();
    }

    private boolean canAccessOwnerPanel() {
        return UserSession.isOwner();
    }
    private boolean isValidOwnerPassword(String password) {
        if (BackendMode.useApiSync()) {
            return UserSession.isOwner() && UserStore.verifyCurrentUserPassword(password);
        }
        for (User user : UserStore.getUsers()) {
            if ("OWNER".equals(user.getRole()) && PasswordUtil.verify(password, user.getPassword())) {
                return true;
            }
        }
        return false;
    }
    private String findOwnerUsername() {
        if (BackendMode.useApiSync()) {
            return UserSession.getUsername() == null ? "admin" : UserSession.getUsername();
        }
        for (User user : UserStore.getUsers()) {
            if ("OWNER".equals(user.getRole())) {
                return user.getUsername();
            }
        }
        return "owner";
    }
    private int earnedPoints = 0;

    private LoyaltyDecision prepareLoyaltyDecision() {
        LoyaltyDecision decision = new LoyaltyDecision();
        decision.phone = phoneField.getText().trim();

        if (decision.phone.isEmpty()) {
            return decision;
        }

        for (var node : invoiceContainer.getChildren()) {
            HBox row = (HBox) node;
            Spinner<Integer> spinner = getRowSpinner(row);
            decision.earnedPoints += spinner.getValue();
        }

        if (CustomerStore.getPoints(decision.phone) >= 1000) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setHeaderText("Use 1000 points for 10 EGP discount?");

            var res = alert.showAndWait();

            if (res.isPresent() && res.get() == ButtonType.OK) {
                decision.redeemUsed = true;
                total = Math.max(0, total - 10);
                updateTotal();
                calculateChange();
            }
        }

        return decision;
    }

    private void applyLoyaltyDecision(LoyaltyDecision decision) {
        earnedPoints = decision == null ? 0 : decision.earnedPoints;
        if (decision == null || decision.phone == null || decision.phone.isBlank()) {
            return;
        }

        if (decision.redeemUsed) {
            CustomerStore.redeem(decision.phone);
        }

        if (decision.earnedPoints > 0) {
            CustomerStore.addPoints(decision.phone, decision.earnedPoints);
        }
    }

    private void rollbackDiscount(double originalTotal, LoyaltyDecision decision) {
        earnedPoints = 0;
        if (decision != null && decision.redeemUsed) {
            total = originalTotal;
            updateTotal();
            calculateChange();
        }
    }

    private static class LoyaltyDecision {
        private String phone = "";
        private int earnedPoints = 0;
        private boolean redeemUsed = false;
    }

    private StackPane buildReceiptLogo() {
        StackPane badge = new StackPane();
        badge.setPrefSize(72, 72);
        badge.setMaxSize(72, 72);
        badge.setStyle(
                "-fx-background-radius:999;" +
                        "-fx-background-color:linear-gradient(to bottom right, #7ed8ff, #3b93f7);" +
                        "-fx-padding:4;"
        );

        StackPane ring = new StackPane();
        ring.setPrefSize(64, 64);
        ring.setMaxSize(64, 64);
        ring.setStyle(
                "-fx-background-radius:999;" +
                        "-fx-background-color:rgba(255,255,255,0.16);" +
                        "-fx-border-color:rgba(226,245,255,0.55);" +
                        "-fx-border-width:1;" +
                        "-fx-border-radius:999;"
        );

        ImageView logoView = new ImageView();
        Image logoImage = loadReceiptLogo();
        if (logoImage != null) {
            logoView.setImage(logoImage);
        }



        logoView.setFitHeight(58);
        logoView.setPreserveRatio(true);
        logoView.setClip(new Circle(29, 29, 29));

        ring.getChildren().add(logoView);
        badge.getChildren().add(ring);
        return badge;
    }

    private Image loadReceiptLogo() {
        var resource = getClass().getResource("/images/abusamir-logo-circle.png");
        return resource == null ? null : new Image(resource.toExternalForm());
    }

}
