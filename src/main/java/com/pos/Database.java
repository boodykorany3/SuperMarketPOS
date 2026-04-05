package com.pos;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/supermarket_pos?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "";

    private static final String URL = clean(System.getenv().getOrDefault("POS_DB_URL", DEFAULT_URL));
    private static final String USER = clean(System.getenv().getOrDefault("POS_DB_USER", DEFAULT_USER));
    private static final String PASS = clean(System.getenv().getOrDefault("POS_DB_PASS", DEFAULT_PASS));
    private static final boolean LOCAL_FALLBACK_ENABLED = Boolean.parseBoolean(
            System.getenv().getOrDefault("POS_LOCAL_FALLBACK", "false")
    );
    private static boolean initialized = false;
    private static boolean available = false;

    private Database() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        username VARCHAR(50) PRIMARY KEY,
                        password VARCHAR(255) NOT NULL,
                        role VARCHAR(20) NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS products (
                        barcode VARCHAR(100) PRIMARY KEY,
                        name VARCHAR(120) NOT NULL,
                        price DOUBLE NOT NULL,
                        quantity INT NOT NULL
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS customers (
                        phone VARCHAR(30) PRIMARY KEY,
                        points INT NOT NULL DEFAULT 0
                    )
                    """);

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sales (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        invoice_number VARCHAR(50) NOT NULL,
                        sale_time DATETIME NOT NULL,
                        total DOUBLE NOT NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                    )
                    """);

            try {
                stmt.execute("ALTER TABLE sales ADD CONSTRAINT uq_sales_invoice UNIQUE (invoice_number)");
            } catch (SQLException ignored) {}

            try {
                stmt.execute("ALTER TABLE sales ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'");
            } catch (SQLException ignored) {}

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS sale_items (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        sale_id BIGINT NOT NULL,
                        product_name VARCHAR(120) NOT NULL,
                        product_barcode VARCHAR(100),
                        quantity INT NOT NULL,
                        CONSTRAINT fk_sale_items_sales
                            FOREIGN KEY (sale_id) REFERENCES sales(id)
                            ON DELETE CASCADE
                    )
                    """);

            try {
                stmt.execute("ALTER TABLE sale_items ADD COLUMN product_barcode VARCHAR(100)");
            } catch (SQLException ignored) {}

            seedUser(conn, "owner", PasswordUtil.hash("1234"), "OWNER");
            seedUser(conn, "admin", PasswordUtil.hash("admin123"), "ADMIN");
            seedUser(conn, "cashier", PasswordUtil.hash("1111"), "CASHIER");

            initialized = true;
            available = true;
        } catch (SQLException e) {
            initialized = true;
            available = false;
            if (LOCAL_FALLBACK_ENABLED) {
                System.err.println("Database initialization failed. Running with local JSON fallback: " + e.getMessage());
                return;
            }
            System.err.println("Database initialization failed and local fallback is disabled: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        try {
            Connection connection = connect();
            available = true;
            return connection;
        } catch (SQLException e) {
            available = false;
            throw e;
        }
    }

    private static Connection connect() throws SQLException {
        try {
            return DriverManager.getConnection(URL, USER, PASS);
        } catch (SQLException primary) {
            if (shouldRetryWithDefaultRootPassword()) {
                try {
                    return DriverManager.getConnection(URL, USER, DEFAULT_PASS);
                } catch (SQLException retryError) {
                    primary.addSuppressed(retryError);
                }
            }
            throw primary;
        }
    }

    private static boolean shouldRetryWithDefaultRootPassword() {
        return "root".equalsIgnoreCase(USER) && !DEFAULT_PASS.equals(PASS);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isLocalFallbackEnabled() {
        return LOCAL_FALLBACK_ENABLED;
    }

    public static boolean useLocalFallback() {
        return LOCAL_FALLBACK_ENABLED && !available;
    }

    public static void requireReady() {
        if (!available && !LOCAL_FALLBACK_ENABLED) {
            throw new IllegalStateException(
                    "Database is unavailable. Start MySQL and configure POS_DB_URL/POS_DB_USER/POS_DB_PASS."
            );
        }
    }

    private static void seedUser(Connection conn, String username, String password, String role) throws SQLException {
        String sql = """
                INSERT INTO users(username, password, role)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE username=username
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.setString(3, role);
            ps.executeUpdate();
        }
    }
}
