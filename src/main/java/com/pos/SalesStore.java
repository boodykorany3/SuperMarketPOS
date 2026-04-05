package com.pos;

import com.google.gson.reflect.TypeToken;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SalesStore {

    public enum ActionResult {
        SUCCESS,
        NOT_FOUND,
        ALREADY_PROCESSED
    }

    public enum AddSaleResult {
        SUCCESS,
        OUT_OF_STOCK,
        FAILED
    }

    private static final Path LOCAL_FILE = Paths.get("sales.json");
    private static final Type SALE_RECORDS_TYPE = new TypeToken<List<SaleRecord>>() {}.getType();
    private static ObservableList<Sale> sales =
            FXCollections.observableArrayList();

    static {
        reload();
    }

    public static ObservableList<Sale> getSales() {
        return sales;
    }

    public static void reload() {
        if (BackendMode.useApiSync()) {
            sales.clear();
            try {
                sales.addAll(PosApiBridge.getSales());
            } catch (Exception e) {
                throw new IllegalStateException("Could not load sales from API.", e);
            }
            return;
        }

        Database.initialize();
        Database.requireReady();
        sales.clear();

        if (Database.useLocalFallback()) {
            List<SaleRecord> records = JsonStorage.read(LOCAL_FILE, SALE_RECORDS_TYPE, new ArrayList<>());
            for (SaleRecord record : records) {
                try {
                    sales.add(new Sale(
                            record.invoiceNumber,
                            LocalDateTime.parse(record.dateTime),
                            record.items == null ? new HashMap<>() : record.items,
                            record.itemBarcodes == null ? new HashMap<>() : record.itemBarcodes,
                            record.itemNamesByBarcode == null ? new HashMap<>() : record.itemNamesByBarcode,
                            record.total,
                            record.status
                    ));
                } catch (DateTimeParseException ignored) {}
            }
            return;
        }

        String salesSql = "SELECT id, invoice_number, sale_time, total, status FROM sales ORDER BY sale_time DESC";

        try (Connection conn = Database.getConnection();
             PreparedStatement salesPs = conn.prepareStatement(salesSql);
             ResultSet salesRs = salesPs.executeQuery()) {

            while (salesRs.next()) {
                long saleId = salesRs.getLong("id");

                Map<String, Integer> items = new HashMap<>();
                Map<String, Integer> itemBarcodes = new HashMap<>();
                Map<String, String> itemNamesByBarcode = new HashMap<>();
                try (PreparedStatement itemsPs = conn.prepareStatement(
                        "SELECT product_name, product_barcode, quantity FROM sale_items WHERE sale_id=?")) {
                    itemsPs.setLong(1, saleId);
                    try (ResultSet itemsRs = itemsPs.executeQuery()) {
                        while (itemsRs.next()) {
                            String productName = itemsRs.getString("product_name");
                            String productBarcode = itemsRs.getString("product_barcode");
                            int quantity = itemsRs.getInt("quantity");

                            if (productName != null && !productName.isBlank()) {
                                items.merge(productName, quantity, Integer::sum);
                            }
                            if (productBarcode != null && !productBarcode.isBlank()) {
                                itemBarcodes.merge(productBarcode, quantity, Integer::sum);
                                if (productName != null && !productName.isBlank()) {
                                    itemNamesByBarcode.putIfAbsent(productBarcode, productName);
                                }
                            }
                        }
                    }
                }

                sales.add(new Sale(
                        salesRs.getString("invoice_number"),
                        salesRs.getTimestamp("sale_time").toLocalDateTime(),
                        items,
                        itemBarcodes,
                        itemNamesByBarcode,
                        salesRs.getDouble("total"),
                        salesRs.getString("status")
                ));
            }

        } catch (Exception e) {
            throw new IllegalStateException("Could not load sales from database.", e);
        }
    }

    public static AddSaleResult addSale(Sale sale) {
        if (sale == null) {
            return AddSaleResult.FAILED;
        }

        if (BackendMode.useApiSync()) {
            AddSaleResult result = PosApiBridge.addSale(sale);
            if (result == AddSaleResult.SUCCESS) {
                sales.add(0, sale);
            }
            return result;
        }

        Database.initialize();
        Database.requireReady();

        if (Database.useLocalFallback()) {
            sales.add(0, sale);
            if (!saveLocal()) {
                sales.remove(0);
                return AddSaleResult.FAILED;
            }
            return AddSaleResult.SUCCESS;
        }

        String saleSql = "INSERT INTO sales(invoice_number, sale_time, total, status) VALUES (?, ?, ?, ?)";
        String updateInvoiceSql = "UPDATE sales SET invoice_number=? WHERE id=?";
        String itemSql = "INSERT INTO sale_items(sale_id, product_name, product_barcode, quantity) VALUES (?, ?, ?, ?)";

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try {
                if (!reserveStock(conn, sale)) {
                    conn.rollback();
                    return AddSaleResult.OUT_OF_STOCK;
                }

                long saleId;
                String provisionalInvoice = "TMP-" + UUID.randomUUID();
                try (PreparedStatement salePs = conn.prepareStatement(saleSql, Statement.RETURN_GENERATED_KEYS)) {
                    salePs.setString(1, provisionalInvoice);
                    salePs.setTimestamp(2, Timestamp.valueOf(sale.getDateTime() == null ? LocalDateTime.now() : sale.getDateTime()));
                    salePs.setDouble(3, sale.getTotal());
                    salePs.setString(4, sale.getStatus());
                    salePs.executeUpdate();

                    try (ResultSet generated = salePs.getGeneratedKeys()) {
                        if (!generated.next()) {
                            conn.rollback();
                            return AddSaleResult.FAILED;
                        }
                        saleId = generated.getLong(1);
                    }
                }

                String finalInvoice = formatInvoiceNumber(saleId);
                try (PreparedStatement updateInvoicePs = conn.prepareStatement(updateInvoiceSql)) {
                    updateInvoicePs.setString(1, finalInvoice);
                    updateInvoicePs.setLong(2, saleId);
                    if (updateInvoicePs.executeUpdate() <= 0) {
                        conn.rollback();
                        return AddSaleResult.FAILED;
                    }
                }

                if (!insertSaleItems(conn, itemSql, saleId, sale)) {
                    conn.rollback();
                    return AddSaleResult.FAILED;
                }

                conn.commit();
                sale.setInvoiceNumber(finalInvoice);
                sales.add(0, sale);
                return AddSaleResult.SUCCESS;
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {}
                return AddSaleResult.FAILED;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            return AddSaleResult.FAILED;
        }
    }

    public static ActionResult cancelInvoice(String invoiceNumber) {
        return processInvoiceAction(invoiceNumber, Sale.STATUS_CANCELED);
    }

    public static ActionResult returnInvoice(String invoiceNumber) {
        return processInvoiceAction(invoiceNumber, Sale.STATUS_RETURNED);
    }

    private static ActionResult processInvoiceAction(String invoiceNumber, String newStatus) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            return ActionResult.NOT_FOUND;
        }

        if (BackendMode.useApiSync()) {
            ActionResult result = Sale.STATUS_CANCELED.equals(newStatus)
                    ? PosApiBridge.cancelSale(invoiceNumber)
                    : PosApiBridge.returnSale(invoiceNumber);
            if (result == ActionResult.SUCCESS) {
                Sale target = findSaleByInvoice(invoiceNumber);
                if (target != null) {
                    target.setStatus(newStatus);
                }
                InventoryStore.reload();
            }
            return result;
        }

        Database.initialize();
        Database.requireReady();

        Sale target = findSaleByInvoice(invoiceNumber);

        if (Database.useLocalFallback()) {
            if (target == null) {
                return ActionResult.NOT_FOUND;
            }
            if (!target.isCompleted()) {
                return ActionResult.ALREADY_PROCESSED;
            }
            restockItemsLocal(target);
            target.setStatus(newStatus);
            if (!saveLocal()) {
                return ActionResult.NOT_FOUND;
            }
            return ActionResult.SUCCESS;
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long saleId = null;
                String currentStatus = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, status FROM sales WHERE invoice_number=? FOR UPDATE")) {
                    ps.setString(1, invoiceNumber);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return ActionResult.NOT_FOUND;
                        }
                        saleId = rs.getLong("id");
                        currentStatus = rs.getString("status");
                    }
                }

                if (!Sale.STATUS_COMPLETED.equals(currentStatus)) {
                    conn.rollback();
                    return ActionResult.ALREADY_PROCESSED;
                }

                try (PreparedStatement updateStatus = conn.prepareStatement("UPDATE sales SET status=? WHERE id=?")) {
                    updateStatus.setString(1, newStatus);
                    updateStatus.setLong(2, saleId);
                    if (updateStatus.executeUpdate() <= 0) {
                        conn.rollback();
                        return ActionResult.NOT_FOUND;
                    }
                }

                if (!restockItemsDb(conn, saleId)) {
                    conn.rollback();
                    return ActionResult.NOT_FOUND;
                }

                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {}
                return ActionResult.NOT_FOUND;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            return ActionResult.NOT_FOUND;
        }

        if (target != null) {
            target.setStatus(newStatus);
        }
        InventoryStore.reload();
        return ActionResult.SUCCESS;
    }

    private static boolean reserveStock(Connection conn, Sale sale) throws Exception {
        Map<String, Integer> byBarcode = sale.getItemBarcodes();
        if (byBarcode != null && !byBarcode.isEmpty()) {
            String sql = "UPDATE products SET quantity = quantity - ? WHERE barcode=? AND quantity >= ?";
            boolean hasAnyItem = false;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Map.Entry<String, Integer> entry : byBarcode.entrySet()) {
                    String barcode = InventoryStore.sanitizeBarcode(entry.getKey());
                    int quantity = entry.getValue() == null ? 0 : entry.getValue();
                    if (barcode.isBlank() || quantity <= 0) {
                        continue;
                    }
                    hasAnyItem = true;
                    ps.setInt(1, quantity);
                    ps.setString(2, barcode);
                    ps.setInt(3, quantity);
                    if (ps.executeUpdate() <= 0) {
                        return false;
                    }
                }
            }
            return hasAnyItem;
        }

        String sql = "UPDATE products SET quantity = quantity - ? WHERE name=? AND quantity >= ? LIMIT 1";
        boolean hasAnyItem = false;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> entry : sale.getItems().entrySet()) {
                String name = entry.getKey();
                int quantity = entry.getValue() == null ? 0 : entry.getValue();
                if (name == null || name.isBlank() || quantity <= 0) {
                    continue;
                }
                hasAnyItem = true;
                ps.setInt(1, quantity);
                ps.setString(2, name);
                ps.setInt(3, quantity);
                if (ps.executeUpdate() <= 0) {
                    return false;
                }
            }
        }
        return hasAnyItem;
    }

    private static boolean insertSaleItems(Connection conn, String itemSql, long saleId, Sale sale) throws Exception {
        try (PreparedStatement itemPs = conn.prepareStatement(itemSql)) {
            boolean hasAnyItem = false;

            if (!sale.getItemBarcodes().isEmpty()) {
                for (Map.Entry<String, Integer> entry : sale.getItemBarcodes().entrySet()) {
                    String barcode = InventoryStore.sanitizeBarcode(entry.getKey());
                    int quantity = entry.getValue() == null ? 0 : entry.getValue();
                    if (barcode.isBlank() || quantity <= 0) {
                        continue;
                    }
                    hasAnyItem = true;
                    itemPs.setLong(1, saleId);
                    itemPs.setString(2, sale.getDisplayNameForBarcode(barcode));
                    itemPs.setString(3, barcode);
                    itemPs.setInt(4, quantity);
                    itemPs.addBatch();
                }
            } else {
                for (Map.Entry<String, Integer> entry : sale.getItems().entrySet()) {
                    String productName = entry.getKey();
                    int quantity = entry.getValue() == null ? 0 : entry.getValue();
                    if (productName == null || productName.isBlank() || quantity <= 0) {
                        continue;
                    }
                    hasAnyItem = true;
                    itemPs.setLong(1, saleId);
                    itemPs.setString(2, productName);
                    itemPs.setString(3, null);
                    itemPs.setInt(4, quantity);
                    itemPs.addBatch();
                }
            }

            if (!hasAnyItem) {
                return false;
            }

            itemPs.executeBatch();
            return true;
        }
    }

    private static boolean restockItemsDb(Connection conn, long saleId) throws Exception {
        String selectSql = "SELECT product_name, product_barcode, quantity FROM sale_items WHERE sale_id=?";
        String restockByBarcodeSql = "UPDATE products SET quantity = quantity + ? WHERE barcode=?";
        String restockByNameSql = "UPDATE products SET quantity = quantity + ? WHERE name=? LIMIT 1";
        boolean hasAnyItem = false;

        try (PreparedStatement selectPs = conn.prepareStatement(selectSql)) {
            selectPs.setLong(1, saleId);
            try (ResultSet rs = selectPs.executeQuery();
                 PreparedStatement byBarcodePs = conn.prepareStatement(restockByBarcodeSql);
                 PreparedStatement byNamePs = conn.prepareStatement(restockByNameSql)) {

                while (rs.next()) {
                    int quantity = rs.getInt("quantity");
                    if (quantity <= 0) {
                        continue;
                    }
                    hasAnyItem = true;

                    String barcode = InventoryStore.sanitizeBarcode(rs.getString("product_barcode"));
                    if (!barcode.isBlank()) {
                        byBarcodePs.setInt(1, quantity);
                        byBarcodePs.setString(2, barcode);
                        if (byBarcodePs.executeUpdate() <= 0) {
                            return false;
                        }
                        continue;
                    }

                    String name = rs.getString("product_name");
                    if (name == null || name.isBlank()) {
                        return false;
                    }
                    byNamePs.setInt(1, quantity);
                    byNamePs.setString(2, name);
                    if (byNamePs.executeUpdate() <= 0) {
                        return false;
                    }
                }
            }
        }

        return hasAnyItem;
    }

    private static void restockItemsLocal(Sale sale) {
        if (sale == null) {
            return;
        }

        Map<String, Integer> byBarcode = sale.getItemBarcodes();
        if (byBarcode != null && !byBarcode.isEmpty()) {
            for (Map.Entry<String, Integer> item : byBarcode.entrySet()) {
                InventoryStore.restockByBarcode(item.getKey(), item.getValue());
            }
            return;
        }

        for (Map.Entry<String, Integer> item : sale.getItems().entrySet()) {
            InventoryStore.restockByName(item.getKey(), item.getValue());
        }
    }

    private static Sale findSaleByInvoice(String invoiceNumber) {
        for (Sale sale : sales) {
            if (invoiceNumber.equals(sale.getInvoiceNumber())) {
                return sale;
            }
        }
        return null;
    }

    private static String formatInvoiceNumber(long saleId) {
        return "Invoice #" + String.format("%06d", saleId);
    }

    private static boolean saveLocal() {
        List<SaleRecord> records = new ArrayList<>();
        for (Sale sale : sales) {
            SaleRecord record = new SaleRecord();
            record.invoiceNumber = sale.getInvoiceNumber();
            record.dateTime = sale.getDateTime() == null ? LocalDateTime.now().toString() : sale.getDateTime().toString();
            record.items = sale.getItems();
            record.itemBarcodes = sale.getItemBarcodes();
            record.itemNamesByBarcode = sale.getItemNamesByBarcode();
            record.total = sale.getTotal();
            record.status = sale.getStatus();
            records.add(record);
        }
        return JsonStorage.writeSafe(LOCAL_FILE, records);
    }

    private static class SaleRecord {
        String invoiceNumber;
        String dateTime;
        Map<String, Integer> items;
        Map<String, Integer> itemBarcodes;
        Map<String, String> itemNamesByBarcode;
        double total;
        String status;
    }
}
