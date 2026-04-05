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
import java.util.ArrayList;
import java.util.List;

public class InventoryStore {

    private static final Path LOCAL_FILE = Paths.get("products.json");
    private static final Type PRODUCT_LIST_TYPE = new TypeToken<List<Product>>() {}.getType();
    private static ObservableList<Product> products =
            FXCollections.observableArrayList();

    static {
        reload();
    }

    public static ObservableList<Product> getProducts() {
        return products;
    }

    public static void reload() {
        if (BackendMode.useApiSync()) {
            products.clear();
            try {
                products.addAll(PosApiBridge.getProducts());
            } catch (Exception e) {
                throw new IllegalStateException("Could not load inventory from API.", e);
            }
            return;
        }

        Database.initialize();
        Database.requireReady();
        products.clear();

        if (Database.useLocalFallback()) {
            List<Product> local = JsonStorage.read(LOCAL_FILE, PRODUCT_LIST_TYPE, new ArrayList<>());
            products.addAll(local);
            return;
        }

        String sql = "SELECT name, barcode, price, quantity FROM products";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                products.add(new Product(
                        rs.getString("name"),
                        rs.getString("barcode"),
                        rs.getDouble("price"),
                        rs.getInt("quantity")
                ));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not load inventory from database.", e);
        }
    }

    // البحث بالاسم (نخليه موجود لو احتجناه)
    public static Product findByName(String name) {
        for (Product p : products) {
            if (p.getName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    // 👇 البحث بالباركود (الجديد)
    public static Product findByBarcode(String code) {
        String normalizedLookup = normalizeBarcode(code);
        if (normalizedLookup.isEmpty()) {
            return null;
        }

        for (Product p : products) {
            if (normalizedLookup.equals(normalizeBarcode(p.getBarcode()))) {
                return p;
            }
        }

        return null;
    }

    public static Product findByBarcodeFlexible(String code) {
        return findByBarcode(code);
    }

    public static String sanitizeBarcode(String value) {
        return normalizeBarcode(value);
    }

    public static String addOrUpdate(Product product) {
        String normalizedBarcode = normalizeBarcode(product.getBarcode());
        if (normalizedBarcode.isEmpty()) {
            Product existingByName = findByName(product.getName());
            if (existingByName != null) {
                normalizedBarcode = normalizeBarcode(existingByName.getBarcode());
            } else {
                normalizedBarcode = generateInternalBarcode();
            }
        }

        Product existing = findByBarcode(normalizedBarcode);
        Product target = existing != null ? existing : product;
        target.setName(product.getName());
        target.setBarcode(normalizedBarcode);
        target.setPrice(product.getPrice());
        target.setQuantity(product.getQuantity());

        if (BackendMode.useApiSync()) {
            try {
                String savedBarcode = PosApiBridge.addOrUpdateProduct(target, normalizedBarcode);
                target.setBarcode(savedBarcode);
                if (existing == null) {
                    products.add(target);
                }
                return savedBarcode;
            } catch (Exception e) {
                throw new IllegalStateException("Could not save product to API.", e);
            }
        }

        if (existing == null) {
            products.add(target);
        }

        Database.initialize();
        Database.requireReady();

        if (Database.useLocalFallback()) {
            saveLocal();
            return normalizedBarcode;
        }

        String sql = """
                INSERT INTO products(name, barcode, price, quantity)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    name=VALUES(name),
                    price=VALUES(price),
                    quantity=VALUES(quantity)
                """;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getName());
            ps.setString(2, normalizedBarcode);
            ps.setDouble(3, product.getPrice());
            ps.setInt(4, product.getQuantity());
            ps.executeUpdate();
            return normalizedBarcode;
        } catch (Exception e) {
            reload();
            throw new IllegalStateException("Could not save product to database.", e);
        }
    }

    public static void updateQuantity(Product product) {
        if (product == null) {
            return;
        }

        if (BackendMode.useApiSync()) {
            String normalizedBarcode = normalizeBarcode(product.getBarcode());
            if (normalizedBarcode.isBlank()) {
                return;
            }
            PosApiBridge.addOrUpdateProduct(product, normalizedBarcode);
            return;
        }

        Database.initialize();
        Database.requireReady();

        if (Database.useLocalFallback()) {
            saveLocal();
            return;
        }

        String sql = "UPDATE products SET quantity=? WHERE barcode=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, product.getQuantity());
            ps.setString(2, product.getBarcode());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not update product quantity.", e);
        }
    }

    public static void restockByName(String productName, int quantityToAdd) {
        if (productName == null || productName.isBlank() || quantityToAdd <= 0) {
            return;
        }

        if (BackendMode.useApiSync()) {
            Product product = findByName(productName);
            if (product == null) {
                return;
            }
            product.setQuantity(product.getQuantity() + quantityToAdd);
            updateQuantity(product);
            return;
        }

        Database.initialize();
        Database.requireReady();

        if (Database.useLocalFallback()) {
            Product product = findByName(productName);
            if (product == null) {
                return;
            }
            product.setQuantity(product.getQuantity() + quantityToAdd);
            updateQuantity(product);
            return;
        }

        String sql = "UPDATE products SET quantity = quantity + ? WHERE name=? LIMIT 1";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantityToAdd);
            ps.setString(2, productName);
            if (ps.executeUpdate() <= 0) {
                return;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not restock product by name.", e);
        }

        Product product = findByName(productName);
        if (product != null) {
            product.setQuantity(product.getQuantity() + quantityToAdd);
        }
    }

    public static void restockByBarcode(String barcode, int quantityToAdd) {
        if (barcode == null || barcode.isBlank() || quantityToAdd <= 0) {
            return;
        }

        String normalizedBarcode = normalizeBarcode(barcode);
        if (normalizedBarcode.isBlank()) {
            return;
        }

        if (BackendMode.useApiSync()) {
            Product product = findByBarcode(normalizedBarcode);
            if (product == null) {
                return;
            }
            product.setQuantity(product.getQuantity() + quantityToAdd);
            updateQuantity(product);
            return;
        }

        Database.initialize();
        Database.requireReady();

        if (Database.useLocalFallback()) {
            Product product = findByBarcode(normalizedBarcode);
            if (product == null) {
                return;
            }
            product.setQuantity(product.getQuantity() + quantityToAdd);
            updateQuantity(product);
            return;
        }

        String sql = "UPDATE products SET quantity = quantity + ? WHERE barcode=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quantityToAdd);
            ps.setString(2, normalizedBarcode);
            if (ps.executeUpdate() <= 0) {
                return;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Could not restock product by barcode.", e);
        }

        Product product = findByBarcode(normalizedBarcode);
        if (product != null) {
            product.setQuantity(product.getQuantity() + quantityToAdd);
        }
    }

    public static List<Product> getLowStockProducts(int threshold) {
        List<Product> lowStock = new ArrayList<>();
        for (Product product : products) {
            if (product.getQuantity() < threshold) {
                lowStock.add(product);
            }
        }
        lowStock.sort((a, b) -> Integer.compare(a.getQuantity(), b.getQuantity()));
        return lowStock;
    }

    public static void delete(Product product) {
        delete(product, false);
    }

    public static void delete(Product product, boolean forceDelete) {
        if (product == null) {
            return;
        }

        if (BackendMode.useApiSync()) {
            try {
                PosApiBridge.deleteProduct(product, forceDelete);
                products.remove(product);
                return;
            } catch (Exception e) {
                throw new IllegalStateException("Could not delete product from API.", e);
            }
        }

        products.remove(product);
        Database.initialize();
        Database.requireReady();
        if (Database.useLocalFallback()) {
            saveLocal();
            return;
        }

        String sql = "DELETE FROM products WHERE barcode=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, product.getBarcode());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException("Could not delete product from database.", e);
        }
    }

    private static void saveLocal() {
        JsonStorage.write(LOCAL_FILE, new ArrayList<>(products));
    }

    private static String normalizeBarcode(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.replace("\uFEFF", "").trim();
        normalized = normalized.replaceAll("[\\p{Cntrl}\\s]+", "");
        normalized = toAsciiDigits(normalized);

        if (normalized.length() > 3
                && normalized.charAt(0) == ']'
                && Character.isLetter(normalized.charAt(1))
                && Character.isDigit(normalized.charAt(2))) {
            normalized = normalized.substring(3);
        }

        return normalized;
    }

    private static String generateInternalBarcode() {
        long maxSequence = 0;
        for (Product product : products) {
            String code = normalizeBarcode(product.getBarcode());
            if (isInternalBarcode(code)) {
                long sequence = Long.parseLong(code.substring(3));
                if (sequence > maxSequence) {
                    maxSequence = sequence;
                }
            }
        }

        long nextSequence = maxSequence + 1;
        return "299" + String.format("%010d", nextSequence);
    }

    private static boolean isInternalBarcode(String code) {
        if (code == null || code.length() != 13 || !code.startsWith("299")) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String toAsciiDigits(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch >= '\u0660' && ch <= '\u0669') {
                out.append((char) ('0' + (ch - '\u0660')));
            } else if (ch >= '\u06F0' && ch <= '\u06F9') {
                out.append((char) ('0' + (ch - '\u06F0')));
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }
}
