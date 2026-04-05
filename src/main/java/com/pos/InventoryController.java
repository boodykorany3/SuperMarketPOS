package com.pos;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InventoryController {
    private static final int TOP_PRODUCTS_LIMIT = 6;
    private static final Pattern API_MESSAGE_PATTERN =
            Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");

    @FXML private TextField nameField;
    @FXML private TextField barcodeField;
    @FXML private TextField priceField;
    @FXML private TextField quantityField;

    @FXML private Button addBtn;
    @FXML private Button deleteBtn;

    @FXML private TableView<Product> tableView;
    @FXML private TableColumn<Product, String> nameCol;
    @FXML private TableColumn<Product, String> barcodeCol;
    @FXML private TableColumn<Product, Double> priceCol;
    @FXML private TableColumn<Product, Integer> qtyCol;
    @FXML private TableColumn<Product, String> statusCol;
    @FXML private Label productsCountLabel;
    @FXML private PieChart topSellingPieChart;
    @FXML private VBox topSellingListBox;
    @FXML private Label topSellingSummaryLabel;

    @FXML
    public void initialize() {
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        barcodeCol.setCellValueFactory(new PropertyValueFactory<>("barcode"));
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        statusCol.setCellValueFactory(cellData -> {
            int qty = cellData.getValue().getQuantity();
            return new SimpleStringProperty(qty < 5 ? "LOW STOCK" : "OK");
        });

        priceCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER_RIGHT);
                setText(empty || item == null ? null : String.format(Locale.US, "%.2f", item));
            }
        });

        qtyCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setAlignment(Pos.CENTER);
                setText(empty || item == null ? null : String.valueOf(item));
            }
        });

        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("inventory-status-low", "inventory-status-ok");
                setAlignment(Pos.CENTER);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                if ("LOW STOCK".equals(item)) {
                    getStyleClass().add("inventory-status-low");
                } else {
                    getStyleClass().add("inventory-status-ok");
                }
            }
        });

        tableView.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("inventory-row-low-stock");
                if (!empty && item != null && item.getQuantity() < 5) {
                    getStyleClass().add("inventory-row-low-stock");
                }
            }
        });

        tableView.setPlaceholder(new Label("No products found"));
        tableView.setItems(InventoryStore.getProducts());
        refreshProductsPanelSummary();
        configureTopSellingChart();
        refreshTopSellingInsights();

        addBtn.setOnAction(e -> addOrUpdate());
        deleteBtn.setOnAction(e -> deleteProduct());
        tableView.setOnMouseClicked(e -> selectRow());

        boolean canManage = canManageInventory();
        addBtn.setDisable(!canManage);
        deleteBtn.setDisable(!canManage);
    }

    private void addOrUpdate() {
        if (!canManageInventory()) {
            showAlert("Only owner/admin can add or update products.");
            return;
        }

        String name = nameField.getText().trim();
        String rawBarcode = barcodeField.getText();
        String barcode = InventoryStore.sanitizeBarcode(rawBarcode);
        boolean barcodeMissing = barcode.isEmpty();

        if (name.isEmpty()) {
            showAlert("Enter product name");
            return;
        }

        double price;
        int quantity;
        try {
            price = Double.parseDouble(priceField.getText().trim());
            quantity = Integer.parseInt(quantityField.getText().trim());
        } catch (Exception e) {
            showAlert("Enter valid price and quantity");
            return;
        }

        if (quantity < 0 || price < 0) {
            showAlert("Price and quantity cannot be negative");
            return;
        }

        try {
            String savedBarcode = InventoryStore.addOrUpdate(new Product(name, rawBarcode, price, quantity));
            tableView.refresh();
            if (barcodeMissing) {
                showInfo("Auto barcode created for " + name + ": " + savedBarcode);
            }
            clearFields();
            refreshProductsPanelSummary();
            refreshTopSellingInsights();
        } catch (Exception e) {
            if (hasHttpStatus(e, 403)) {
                showAlert("Only owner/admin can add or update products.");
            } else if (hasHttpStatus(e, 401)) {
                showAlert("Session expired. Please login again.");
            } else {
                String apiMessage = extractApiMessage(e);
                if (!apiMessage.isBlank()) {
                    showAlert(apiMessage);
                } else {
                    showAlert("Could not save product. Check API/server and try again.");
                }
            }
        }
    }

    private void deleteProduct() {
        if (!canManageInventory()) {
            showAlert("Only owner/admin can delete products.");
            return;
        }

        Product selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Select a product first");
            return;
        }
        try {
            InventoryStore.delete(selected);
            tableView.refresh();
            clearFields();
            refreshProductsPanelSummary();
            refreshTopSellingInsights();
        } catch (Exception e) {
            if (hasHttpStatus(e, 403)) {
                showAlert("Only owner/admin can delete products.");
            } else if (hasHttpStatus(e, 401)) {
                showAlert("Session expired. Please login again.");
            } else if (hasHttpStatus(e, 409)) {
                String apiMessage = normalizeDeleteErrorMessage(extractApiMessage(e));
                if (isUsedInSalesMessage(apiMessage) && confirmForceDelete(selected)) {
                    try {
                        InventoryStore.delete(selected, true);
                        tableView.refresh();
                        clearFields();
                        refreshProductsPanelSummary();
                        refreshTopSellingInsights();
                        showInfo("Product was deleted permanently.");
                        return;
                    } catch (Exception forceDeleteError) {
                        String forceMessage = normalizeDeleteErrorMessage(extractApiMessage(forceDeleteError));
                        if (!forceMessage.isBlank()) {
                            showAlert(forceMessage);
                        } else {
                            showAlert("Could not force delete product. Check API/server and try again.");
                        }
                        return;
                    }
                }

                if (!apiMessage.isBlank()) {
                    showAlert(apiMessage);
                } else {
                    showAlert("Product cannot be deleted because it is used in sales.");
                }
            } else {
                String apiMessage = normalizeDeleteErrorMessage(extractApiMessage(e));
                if (!apiMessage.isBlank()) {
                    showAlert(apiMessage);
                } else {
                    showAlert("Could not delete product. Check API/server and try again.");
                }
            }
        }
    }

    private void selectRow() {
        Product selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        nameField.setText(selected.getName());
        barcodeField.setText(selected.getBarcode());
        priceField.setText(String.valueOf(selected.getPrice()));
        quantityField.setText(String.valueOf(selected.getQuantity()));
    }

    private void clearFields() {
        nameField.clear();
        barcodeField.clear();
        priceField.clear();
        quantityField.clear();
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(msg);
        alert.show();
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(msg);
        alert.show();
    }

    private void refreshProductsPanelSummary() {
        if (productsCountLabel == null) {
            return;
        }
        int totalProducts = tableView.getItems() == null ? 0 : tableView.getItems().size();
        int lowStockCount = InventoryStore.getLowStockProducts(5).size();
        productsCountLabel.setText("Products: " + totalProducts + " | Low stock: " + lowStockCount);
    }

    private void configureTopSellingChart() {
        topSellingPieChart.setLegendVisible(false);
        topSellingPieChart.setLabelsVisible(false);
        topSellingPieChart.setAnimated(false);
    }

    private void refreshTopSellingInsights() {
        topSellingPieChart.setData(FXCollections.observableArrayList());
        topSellingListBox.getChildren().clear();

        try {
            SalesStore.reload();
        } catch (Exception e) {
            topSellingSummaryLabel.setText("Could not load sales data.");
            Label item = new Label("Sales data is unavailable right now.");
            item.getStyleClass().add("inventory-insights-empty");
            topSellingListBox.getChildren().add(item);
            return;
        }

        Map<String, Integer> soldByProduct = collectSoldQuantitiesByProduct();

        int totalSoldUnits = 0;
        for (Integer value : soldByProduct.values()) {
            if (value != null && value > 0) {
                totalSoldUnits += value;
            }
        }

        if (totalSoldUnits <= 0) {
            topSellingSummaryLabel.setText("No completed invoices yet.");
            Label item = new Label("No completed sales to analyze.");
            item.getStyleClass().add("inventory-insights-empty");
            topSellingListBox.getChildren().add(item);
            return;
        }

        List<Map.Entry<String, Integer>> ranked = new ArrayList<>(soldByProduct.entrySet());
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int topCount = Math.min(TOP_PRODUCTS_LIMIT, ranked.size());
        var pieData = FXCollections.<PieChart.Data>observableArrayList();

        for (int i = 0; i < topCount; i++) {
            Map.Entry<String, Integer> entry = ranked.get(i);
            double ratio = (entry.getValue() * 100.0) / totalSoldUnits;
            String label = shortLabel(entry.getKey()) + " (" + String.format(Locale.US, "%.1f", ratio) + "%)";
            pieData.add(new PieChart.Data(label, entry.getValue()));
        }

        int remainingUnits = 0;
        for (int i = topCount; i < ranked.size(); i++) {
            remainingUnits += ranked.get(i).getValue();
        }
        if (remainingUnits > 0) {
            double ratio = (remainingUnits * 100.0) / totalSoldUnits;
            String label = "Others (" + String.format(Locale.US, "%.1f", ratio) + "%)";
            pieData.add(new PieChart.Data(label, remainingUnits));
        }

        topSellingPieChart.setData(pieData);
        topSellingSummaryLabel.setText("Based on completed invoices | Total sold units: " + totalSoldUnits);

        for (int i = 0; i < topCount; i++) {
            Map.Entry<String, Integer> entry = ranked.get(i);
            double ratio = (entry.getValue() * 100.0) / totalSoldUnits;
            Label row = new Label(
                    String.format(Locale.US, "%d) %s - %d units (%.1f%%)",
                            i + 1,
                            entry.getKey(),
                            entry.getValue(),
                            ratio)
            );
            row.getStyleClass().add("inventory-insights-item");
            if (i < 3) {
                row.getStyleClass().add("inventory-insights-rank-" + (i + 1));
            }
            topSellingListBox.getChildren().add(row);
        }
    }

    private Map<String, Integer> collectSoldQuantitiesByProduct() {
        Map<String, Integer> soldByProduct = new HashMap<>();

        for (Sale sale : SalesStore.getSales()) {
            if (sale == null || !sale.isCompleted()) {
                continue;
            }

            Map<String, Integer> byBarcode = sale.getItemBarcodes();
            if (byBarcode != null && !byBarcode.isEmpty()) {
                for (Map.Entry<String, Integer> entry : byBarcode.entrySet()) {
                    int quantity = entry.getValue() == null ? 0 : entry.getValue();
                    if (quantity <= 0) {
                        continue;
                    }
                    String productName = resolveSoldItemName(sale, entry.getKey());
                    soldByProduct.merge(productName, quantity, Integer::sum);
                }
                continue;
            }

            for (Map.Entry<String, Integer> entry : sale.getItems().entrySet()) {
                int quantity = entry.getValue() == null ? 0 : entry.getValue();
                if (quantity <= 0) {
                    continue;
                }
                String productName = cleanProductName(entry.getKey());
                soldByProduct.merge(productName, quantity, Integer::sum);
            }
        }

        return soldByProduct;
    }

    private String resolveSoldItemName(Sale sale, String rawBarcode) {
        String barcode = InventoryStore.sanitizeBarcode(rawBarcode);
        if (barcode.isBlank()) {
            return "Unknown product";
        }

        Product inStock = InventoryStore.findByBarcode(barcode);
        if (inStock != null) {
            String name = cleanProductName(inStock.getName());
            if (!"Unknown product".equals(name)) {
                return name;
            }
        }

        String fromSale = sale.getDisplayNameForBarcode(barcode);
        if (fromSale != null && !fromSale.isBlank() && !barcode.equals(fromSale)) {
            return cleanProductName(fromSale);
        }

        return "Barcode " + barcode;
    }

    private String cleanProductName(String value) {
        if (value == null) {
            return "Unknown product";
        }
        String clean = value.trim();
        return clean.isEmpty() ? "Unknown product" : clean;
    }

    private String shortLabel(String value) {
        if (value == null) {
            return "Unknown";
        }
        String clean = value.trim();
        if (clean.length() <= 22) {
            return clean;
        }
        return clean.substring(0, 19) + "...";
    }

    private boolean canManageInventory() {
        return UserSession.isOwner() || UserSession.isAdmin();
    }

    private boolean hasHttpStatus(Throwable throwable, int statusCode) {
        String apiMarker = "API error " + statusCode;
        String jsonMarker = "\"status\":" + statusCode;
        String jsonMarkerWithSpace = "\"status\": " + statusCode;
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && (message.contains(apiMarker)
                    || message.contains(jsonMarker)
                    || message.contains(jsonMarkerWithSpace))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String extractApiMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                Matcher matcher = API_MESSAGE_PATTERN.matcher(message);
                if (matcher.find()) {
                    String extracted = matcher.group(1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .trim();
                    if (!extracted.isBlank()) {
                        return extracted;
                    }
                }
            }
            current = current.getCause();
        }
        return "";
    }

    private String normalizeDeleteErrorMessage(String apiMessage) {
        if (apiMessage == null || apiMessage.isBlank()) {
            return "";
        }
        String clean = apiMessage.trim();
        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("foreign key constraint")
                || lower.contains("cannot delete or update a parent row")
                || lower.contains("sale_items")
                || lower.contains("product_id")
                || lower.contains("delete from products where id")) {
            return "Product cannot be deleted because it is used in sales.";
        }
        return clean;
    }

    private boolean isUsedInSalesMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("used in sales")
                || lower.contains("foreign key constraint")
                || lower.contains("cannot delete or update a parent row")
                || lower.contains("sale_items")
                || lower.contains("product_id");
    }

    private boolean confirmForceDelete(Product product) {
        String name = product == null || product.getName() == null || product.getName().isBlank()
                ? "selected product"
                : product.getName();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Force Delete Product");
        confirm.setHeaderText("This product is used in sales.");
        confirm.setContentText(
                "Do you want to force delete \"" + name + "\" permanently?\n"
                        + "This will also remove it from old invoices that contain it."
        );
        return confirm.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }
}
