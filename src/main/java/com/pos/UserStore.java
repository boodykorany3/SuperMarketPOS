package com.pos;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserStore {

    private static final Path LOCAL_FILE = Paths.get("users.json");
    private static final Type USER_RECORDS_TYPE = new TypeToken<List<UserRecord>>() {}.getType();

    public static List<User> getUsers() {
        if (BackendMode.useApiSync()) {
            try {
                return PosApiBridge.getUsers();
            } catch (Exception e) {
                throw new IllegalStateException("Could not load users from API.", e);
            }
        }

        Database.initialize();
        Database.requireReady();

        if (Database.useLocalFallback()) {
            return loadLocalUsers();
        }

        List<User> users = new ArrayList<>();

        String sql = "SELECT username, password, role FROM users";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String username = rs.getString("username");
                String password = rs.getString("password");
                String role = rs.getString("role");

                if (!PasswordUtil.isHashed(password)) {
                    password = PasswordUtil.hash(password);
                    updateDbPassword(conn, username, password);
                }

                users.add(new User(
                        username,
                        password,
                        role
                ));
            }
        } catch (Exception e) {
            if (Database.useLocalFallback()) {
                return loadLocalUsers();
            }
            throw new IllegalStateException("Could not load users from database.", e);
        }

        if (users.isEmpty() && Database.useLocalFallback()) {
            users = defaultUsers();
        }

        return users;
    }

    public static User login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        if (BackendMode.useApiSync()) {
            PosApiBridge.LoginResult result = PosApiBridge.login(username.trim(), password);
            if (result == null || result.getToken() == null || result.getToken().isBlank()) {
                return null;
            }

            UserSession.setUserId(result.getUserId());
            UserSession.setUsername(result.getUsername());
            UserSession.setRole(result.getRole());
            UserSession.setBranchId(result.getBranchId());
            UserSession.setBranchCode(result.getBranchCode());
            UserSession.setBranchName(result.getBranchName());
            UserSession.setAuthToken(result.getToken());
            return new User(
                    result.getUsername(),
                    "",
                    result.getRole(),
                    result.getBranchId(),
                    result.getBranchCode(),
                    result.getBranchName()
            );
        }

        for (User user : getUsers()) {
            if (user.getUsername().equals(username.trim()) && PasswordUtil.verify(password, user.getPassword())) {
                UserSession.setUsername(user.getUsername());
                UserSession.setRole(user.getRole());
                UserSession.setUserId(null);
                UserSession.setBranchId(null);
                UserSession.setBranchCode("");
                UserSession.setBranchName("");
                UserSession.setAuthToken(null);
                return user;
            }
        }
        return null;
    }

    public static boolean verifyCurrentUserPassword(String password) {
        if (password == null || password.isBlank()) {
            return false;
        }

        String current = UserSession.getUsername();
        if (current == null || current.isBlank()) {
            return false;
        }

        if (BackendMode.useApiSync()) {
            PosApiBridge.LoginResult result = PosApiBridge.login(current, password);
            return result != null;
        }

        for (User user : getUsers()) {
            if (current.equals(user.getUsername()) && PasswordUtil.verify(password, user.getPassword())) {
                return true;
            }
        }
        return false;
    }

    public static boolean changePassword(String username, String currentPassword, String newPassword) {
        if (username == null || username.isBlank()
                || currentPassword == null || currentPassword.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            return false;
        }

        if (BackendMode.useApiSync()) {
            try {
                return PosApiBridge.changePassword(currentPassword, newPassword);
            } catch (Exception ignored) {
                return false;
            }
        }

        List<User> users = getUsers();
        int targetIndex = -1;

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            if (user.getUsername().equals(username)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex < 0) {
            return false;
        }

        User target = users.get(targetIndex);
        if (!PasswordUtil.verify(currentPassword, target.getPassword())) {
            return false;
        }

        String hashed = PasswordUtil.hash(newPassword);
        users.set(targetIndex, new User(target.getUsername(), hashed, target.getRole()));

        if (Database.useLocalFallback()) {
            saveLocalUsers(users);
            return true;
        }

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE users SET password=? WHERE username=?")) {
            ps.setString(1, hashed);
            ps.setString(2, username);
            return ps.executeUpdate() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static List<User> loadLocalUsers() {
        List<UserRecord> records = JsonStorage.read(LOCAL_FILE, USER_RECORDS_TYPE, null);
        if (records == null || records.isEmpty()) {
            List<User> defaults = defaultUsers();
            saveLocalUsers(defaults);
            return defaults;
        }

        List<User> users = new ArrayList<>();
        boolean changed = false;
        for (UserRecord record : records) {
            String password = record.password;
            if (!PasswordUtil.isHashed(password)) {
                password = PasswordUtil.hash(password);
                changed = true;
            }
            users.add(new User(record.username, password, record.role));
        }

        if (changed) {
            saveLocalUsers(users);
        }
        return users;
    }

    private static void saveLocalUsers(List<User> users) {
        List<UserRecord> records = new ArrayList<>();
        for (User user : users) {
            UserRecord record = new UserRecord();
            record.username = user.getUsername();
            record.password = user.getPassword();
            record.role = user.getRole();
            records.add(record);
        }
        JsonStorage.write(LOCAL_FILE, records);
    }

    private static List<User> defaultUsers() {
        List<User> users = new ArrayList<>();
        users.add(new User("owner", PasswordUtil.hash("1234"), "OWNER"));
        users.add(new User("admin", PasswordUtil.hash("admin123"), "ADMIN"));
        users.add(new User("cashier", PasswordUtil.hash("1111"), "CASHIER"));
        return users;
    }

    private static class UserRecord {
        String username;
        String password;
        String role;
    }

    private static void updateDbPassword(Connection conn, String username, String newPassword) {
        try (PreparedStatement update = conn.prepareStatement("UPDATE users SET password=? WHERE username=?")) {
            update.setString(1, newPassword);
            update.setString(2, username);
            update.executeUpdate();
        } catch (Exception ignored) {}
    }
}
