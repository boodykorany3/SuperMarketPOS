package com.pos;

public class UserSession {

    private static final String API_OWNER_USERNAME = resolveApiOwnerUsername();
    private static String currentRole;
    private static String currentUsername;
    private static Long currentUserId;
    private static Long currentBranchId;
    private static String currentBranchCode;
    private static String currentBranchName;
    private static String authToken;

    public static void setRole(String role) {
        currentRole = role;
    }

    public static void setUsername(String username) {
        currentUsername = username;
    }

    public static String getRole() {
        return currentRole;
    }

    public static String getUsername() {
        return currentUsername;
    }

    public static Long getUserId() {
        return currentUserId;
    }

    public static void setUserId(Long userId) {
        currentUserId = userId;
    }

    public static Long getBranchId() {
        return currentBranchId;
    }

    public static void setBranchId(Long branchId) {
        currentBranchId = branchId;
    }

    public static String getBranchCode() {
        return currentBranchCode;
    }

    public static void setBranchCode(String branchCode) {
        currentBranchCode = branchCode;
    }

    public static String getBranchName() {
        return currentBranchName;
    }

    public static void setBranchName(String branchName) {
        currentBranchName = branchName;
    }

    public static String getAuthToken() {
        return authToken;
    }

    public static void setAuthToken(String token) {
        authToken = token;
    }

    public static boolean isOwner() {
        if ("OWNER".equals(currentRole)) {
            return true;
        }
        if (!BackendMode.useApiSync() || !"ADMIN".equals(currentRole)) {
            return false;
        }
        String username = currentUsername == null ? "" : currentUsername.trim();
        return !username.isEmpty() && API_OWNER_USERNAME.equalsIgnoreCase(username);
    }

    public static boolean isAdmin() {
        return "ADMIN".equals(currentRole);
    }

    public static boolean canChangePassword() {
        return isOwner() || isAdmin();
    }

    public static void clear() {
        currentRole = null;
        currentUsername = null;
        currentUserId = null;
        currentBranchId = null;
        currentBranchCode = null;
        currentBranchName = null;
        authToken = null;
    }

    private static String resolveApiOwnerUsername() {
        String value = System.getenv().getOrDefault("POS_OWNER_USERNAME", "owner");
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? "owner" : normalized;
    }
}
