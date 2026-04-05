package com.pos;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SalesHistoryView {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final String STATUS_ALL = "ALL";

    public static VBox getView() {
        VBox root = new VBox();
        root.getStyleClass().add("reports-root");
        root.setFillWidth(true);
        root.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        rebuild(root, "", STATUS_ALL);
        return root;
    }

    private static void rebuild(VBox root, String query, String statusFilter) {
        String safeQuery = query == null ? "" : query.trim();
        String safeStatusFilter = normalizeStatus(statusFilter);

        List<Sale> allSales = new ArrayList<>(SalesStore.getSales());
        allSales.sort(Comparator.comparing(
                Sale::getDateTime,
                Comparator.nullsLast(Comparator.naturalOrder())
        ).reversed());

        List<Sale> filteredSales = allSales.stream()
                .filter(sale -> matchesQuery(sale, safeQuery))
                .filter(sale -> STATUS_ALL.equals(safeStatusFilter) || safeStatusFilter.equals(sale.getStatus()))
                .toList();

        root.getChildren().clear();

        HBox header = new HBox(14);
        header.getStyleClass().add("reports-header");

        VBox titleBox = new VBox(3);
        Label title = new Label("Reports");
        title.getStyleClass().add("reports-title");
        Label subtitle = new Label("Track sales activity and manage invoice state changes.");
        subtitle.getStyleClass().add("reports-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("reports-refresh-btn");
        refreshBtn.setOnAction(e -> rebuild(root, safeQuery, safeStatusFilter));

        Pane headerSpacer = new Pane();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        header.getChildren().addAll(titleBox, headerSpacer, refreshBtn);

        HBox summaryRow = buildSummaryRow(allSales);

        HBox filtersRow = new HBox(10);
        filtersRow.getStyleClass().add("reports-filters-row");

        TextField searchField = new TextField(safeQuery);
        searchField.setPromptText("Search by invoice or date...");
        searchField.getStyleClass().add("reports-search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        ComboBox<String> statusBox = new ComboBox<>();
        statusBox.getItems().addAll(
                STATUS_ALL,
                Sale.STATUS_COMPLETED,
                Sale.STATUS_CANCELED,
                Sale.STATUS_RETURNED
        );
        statusBox.setValue(safeStatusFilter);
        statusBox.getStyleClass().add("reports-status-filter");

        Button applyBtn = new Button("Apply");
        applyBtn.getStyleClass().add("reports-apply-btn");

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("reports-reset-btn");

        Runnable applyFilters = () -> rebuild(root, searchField.getText(), statusBox.getValue());
        applyBtn.setOnAction(e -> applyFilters.run());
        searchField.setOnAction(e -> applyFilters.run());
        statusBox.setOnAction(e -> applyFilters.run());
        resetBtn.setOnAction(e -> rebuild(root, "", STATUS_ALL));

        filtersRow.getChildren().addAll(searchField, statusBox, applyBtn, resetBtn);

        Label resultCount = new Label(filteredSales.size() + " invoice(s) shown");
        resultCount.getStyleClass().add("reports-result-count");

        VBox listBox = new VBox(10);
        listBox.getStyleClass().add("reports-list");

        if (filteredSales.isEmpty()) {
            VBox emptyCard = new VBox(4);
            emptyCard.getStyleClass().add("reports-empty-card");
            Label emptyTitle = new Label("No invoices found");
            emptyTitle.getStyleClass().add("reports-empty-title");
            Label emptyHint = new Label("Try changing search text or status filter.");
            emptyHint.getStyleClass().add("reports-empty-hint");
            emptyCard.getChildren().addAll(emptyTitle, emptyHint);
            listBox.getChildren().add(emptyCard);
        } else {
            for (Sale sale : filteredSales) {
                listBox.getChildren().add(buildSaleCard(root, sale, safeQuery, safeStatusFilter));
            }
        }

        ScrollPane scrollPane = new ScrollPane(listBox);
        scrollPane.setFitToWidth(true);
        scrollPane.getStyleClass().add("reports-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        root.getChildren().addAll(
                header,
                summaryRow,
                filtersRow,
                resultCount,
                scrollPane
        );
    }

    private static HBox buildSummaryRow(List<Sale> sales) {
        long completed = sales.stream().filter(s -> Sale.STATUS_COMPLETED.equals(s.getStatus())).count();
        long canceled = sales.stream().filter(s -> Sale.STATUS_CANCELED.equals(s.getStatus())).count();
        long returned = sales.stream().filter(s -> Sale.STATUS_RETURNED.equals(s.getStatus())).count();
        double completedRevenue = sales.stream()
                .filter(s -> Sale.STATUS_COMPLETED.equals(s.getStatus()))
                .mapToDouble(Sale::getTotal)
                .sum();

        HBox summaryRow = new HBox(10);
        summaryRow.getStyleClass().add("reports-summary-row");
        summaryRow.getChildren().addAll(
                createSummaryCard("Invoices", String.valueOf(sales.size()), "reports-stat-card-neutral"),
                createSummaryCard("Completed", String.valueOf(completed), "reports-stat-card-success"),
                createSummaryCard("Canceled/Returned", (canceled + returned) + "", "reports-stat-card-warning"),
                createSummaryCard("Net Revenue", formatMoney(completedRevenue), "reports-stat-card-revenue")
        );
        return summaryRow;
    }

    private static VBox createSummaryCard(String labelText, String valueText, String accentClass) {
        VBox card = new VBox(4);
        card.getStyleClass().addAll("reports-stat-card", accentClass);

        Label label = new Label(labelText);
        label.getStyleClass().add("reports-stat-label");

        Label value = new Label(valueText);
        value.getStyleClass().add("reports-stat-value");

        card.getChildren().addAll(label, value);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private static VBox buildSaleCard(VBox root, Sale sale, String query, String statusFilter) {
        VBox card = new VBox(10);
        card.getStyleClass().add("reports-sale-card");

        Label invoice = new Label(sale.getInvoiceNumber());
        invoice.getStyleClass().add("reports-invoice");

        Label statusBadge = new Label(sale.getStatus());
        styleStatus(statusBadge, sale.getStatus());

        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(10, invoice, spacer, statusBadge);
        topRow.getStyleClass().add("reports-sale-top-row");

        int itemCount = sale.getItems() == null
                ? 0
                : sale.getItems().values().stream().mapToInt(Integer::intValue).sum();

        Label dateLabel = new Label("Date: " + formatDateTime(sale));
        dateLabel.getStyleClass().add("reports-meta");

        Label itemCountLabel = new Label("Items: " + itemCount);
        itemCountLabel.getStyleClass().add("reports-meta");

        Label totalLabel = new Label("Total: " + formatMoney(sale.getTotal()));
        totalLabel.getStyleClass().add("reports-total");

        HBox infoRow = new HBox(18, dateLabel, itemCountLabel, totalLabel);
        infoRow.getStyleClass().add("reports-info-row");

        HBox actions = new HBox(8);
        actions.getStyleClass().add("reports-actions-row");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("reports-action-btn", "reports-cancel-btn");

        Button returnBtn = new Button("Return");
        returnBtn.getStyleClass().addAll("reports-action-btn", "reports-return-btn");

        boolean canProcess = sale.isCompleted();
        cancelBtn.setDisable(!canProcess);
        returnBtn.setDisable(!canProcess);

        cancelBtn.setOnAction(e -> {
            if (confirm("Cancel invoice " + sale.getInvoiceNumber() + "?")) {
                SalesStore.ActionResult result = SalesStore.cancelInvoice(sale.getInvoiceNumber());
                showResult(result);
                rebuild(root, query, statusFilter);
            }
        });

        returnBtn.setOnAction(e -> {
            if (confirm("Return invoice " + sale.getInvoiceNumber() + "?")) {
                SalesStore.ActionResult result = SalesStore.returnInvoice(sale.getInvoiceNumber());
                showResult(result);
                rebuild(root, query, statusFilter);
            }
        });

        actions.getChildren().addAll(cancelBtn, returnBtn);
        card.getChildren().addAll(topRow, infoRow, actions);
        return card;
    }

    private static boolean matchesQuery(Sale sale, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String lowered = query.toLowerCase();
        String invoice = sale.getInvoiceNumber() == null ? "" : sale.getInvoiceNumber().toLowerCase();
        String date = sale.getDateTime() == null ? "" : sale.getDateTime().toString().toLowerCase();
        return invoice.contains(lowered) || date.contains(lowered);
    }

    private static String normalizeStatus(String value) {
        if (Sale.STATUS_COMPLETED.equals(value)
                || Sale.STATUS_CANCELED.equals(value)
                || Sale.STATUS_RETURNED.equals(value)) {
            return value;
        }
        return STATUS_ALL;
    }

    private static String formatDateTime(Sale sale) {
        if (sale.getDateTime() == null) {
            return "-";
        }
        return sale.getDateTime().format(DATE_FORMAT);
    }

    private static String formatMoney(double amount) {
        return MONEY_FORMAT.format(amount) + " EGP";
    }

    private static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setHeaderText(message);
        return alert.showAndWait().filter(btn -> btn == ButtonType.OK).isPresent();
    }

    private static void showResult(SalesStore.ActionResult result) {
        if (result == SalesStore.ActionResult.SUCCESS) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Invoice updated and stock restored");
            alert.show();
            return;
        }

        Alert alert = new Alert(Alert.AlertType.WARNING);
        if (result == SalesStore.ActionResult.ALREADY_PROCESSED) {
            alert.setHeaderText("This invoice is already canceled/returned");
        } else {
            alert.setHeaderText("Invoice not found");
        }
        alert.show();
    }

    private static void styleStatus(Label statusLabel, String status) {
        statusLabel.getStyleClass().add("reports-status-badge");
        if (Sale.STATUS_CANCELED.equals(status)) {
            statusLabel.getStyleClass().add("reports-status-canceled");
            return;
        }
        if (Sale.STATUS_RETURNED.equals(status)) {
            statusLabel.getStyleClass().add("reports-status-returned");
            return;
        }
        statusLabel.getStyleClass().add("reports-status-completed");
    }
}
