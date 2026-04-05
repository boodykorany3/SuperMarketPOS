package com.pos;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public class CustomerStore {

    private static final Path LOCAL_FILE = Paths.get("customers.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, Integer>>() {}.getType();

    public static int getPoints(String phone) {
        if (BackendMode.useApiSync()) {
            try {
                return PosApiBridge.getCustomerPoints(phone);
            } catch (Exception ignored) {
                return 0;
            }
        }

        Database.initialize();
        Database.requireReady();
        if (Database.useLocalFallback()) {
            Map<String, Integer> map = loadLocal();
            return map.getOrDefault(phone, 0);
        }

        String sql = "SELECT points FROM customers WHERE phone=?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, phone);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("points");
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }

    public static void addPoints(String phone, int value) {
        if (phone == null || phone.isBlank()) {
            return;
        }

        if (BackendMode.useApiSync()) {
            try {
                PosApiBridge.addCustomerPoints(phone, value);
            } catch (Exception ignored) {}
            return;
        }

        Database.initialize();
        Database.requireReady();
        if (Database.useLocalFallback()) {
            Map<String, Integer> map = loadLocal();
            map.put(phone, map.getOrDefault(phone, 0) + value);
            saveLocal(map);
            return;
        }

        String sql = """
                INSERT INTO customers(phone, points)
                VALUES (?, ?)
                ON DUPLICATE KEY UPDATE points=points + VALUES(points)
                """;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            ps.setInt(2, value);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }

    public static boolean redeem(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }

        if (BackendMode.useApiSync()) {
            try {
                return PosApiBridge.redeemCustomerPoints(phone, 1000);
            } catch (Exception ignored) {
                return false;
            }
        }

        Database.initialize();
        Database.requireReady();

        if (Database.useLocalFallback()) {
            int points = getPoints(phone);
            if (points < 1000) {
                return false;
            }
            Map<String, Integer> map = loadLocal();
            map.put(phone, points - 1000);
            saveLocal(map);
            return true;
        }

        String sql = "UPDATE customers SET points = points - 1000 WHERE phone=? AND points >= 1000";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, phone);
            return ps.executeUpdate() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static Map<String,Integer> getAll(){
        if (BackendMode.useApiSync()) {
            try {
                return PosApiBridge.getAllCustomerPoints();
            } catch (Exception ignored) {
                return new HashMap<>();
            }
        }

        Database.initialize();
        Database.requireReady();
        if (Database.useLocalFallback()) {
            return loadLocal();
        }

        Map<String, Integer> all = new HashMap<>();

        String sql = "SELECT phone, points FROM customers ORDER BY phone";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                all.put(rs.getString("phone"), rs.getInt("points"));
            }
        } catch (Exception ignored) {}

        return all;
    }

    private static Map<String, Integer> loadLocal() {
        return JsonStorage.read(LOCAL_FILE, MAP_TYPE, new HashMap<>());
    }

    private static void saveLocal(Map<String, Integer> map) {
        JsonStorage.write(LOCAL_FILE, map);
    }
}
