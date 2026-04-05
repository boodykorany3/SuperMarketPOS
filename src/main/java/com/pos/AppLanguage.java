package com.pos;

import java.util.Locale;

public enum AppLanguage {
    AR("ar", new Locale("ar"), "language.ar"),
    EN("en", Locale.ENGLISH, "language.en");

    private final String code;
    private final Locale locale;
    private final String displayKey;

    AppLanguage(String code, Locale locale, String displayKey) {
        this.code = code;
        this.locale = locale;
        this.displayKey = displayKey;
    }

    public String getCode() {
        return code;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getDisplayKey() {
        return displayKey;
    }

    public static AppLanguage fromCode(String code) {
        if (code == null) {
            return AR;
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        for (AppLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        return AR;
    }
}
