package com.pos;

public final class BackendMode {

    private static final boolean API_SYNC_ENABLED = Boolean.parseBoolean(
            System.getenv().getOrDefault("POS_API_SYNC", "true")
    );

    private static final String API_BASE_URL = clean(
            System.getenv().getOrDefault("POS_API_BASE_URL", "http://127.0.0.1:8080")
    );

    private BackendMode() {}

    public static boolean useApiSync() {
        return API_SYNC_ENABLED;
    }

    public static String apiBaseUrl() {
        return API_BASE_URL;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
