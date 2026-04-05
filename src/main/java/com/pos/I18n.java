package com.pos;

import javafx.fxml.FXMLLoader;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class I18n {
    private static final String BUNDLE_BASE_NAME = "i18n.messages";
    private static final Path SETTINGS_PATH = Paths.get("app-settings.json");
    private static final String FALLBACK_VALUE_PREFIX = "??";

    private static AppLanguage currentLanguage;
    private static ResourceBundle currentBundle;

    static {
        currentLanguage = readLanguageFromSettings();
        currentBundle = loadBundle(currentLanguage);
        Locale.setDefault(currentLanguage.getLocale());
    }

    private I18n() {
    }

    public static synchronized AppLanguage getLanguage() {
        return currentLanguage;
    }

    public static synchronized ResourceBundle getBundle() {
        return currentBundle;
    }

    public static synchronized void setLanguage(AppLanguage language) {
        AppLanguage selected = language == null ? AppLanguage.AR : language;
        if (selected == currentLanguage) {
            return;
        }

        currentLanguage = selected;
        currentBundle = loadBundle(selected);
        Locale.setDefault(selected.getLocale());
        saveLanguageToSettings(selected);
    }

    public static FXMLLoader loader(String fxmlPath) {
        return new FXMLLoader(I18n.class.getResource(fxmlPath), getBundle());
    }

    public static String t(String key, Object... args) {
        if (key == null || key.isBlank()) {
            return "";
        }

        String pattern;
        try {
            pattern = getBundle().getString(key);
        } catch (MissingResourceException ignored) {
            return FALLBACK_VALUE_PREFIX + key;
        }

        if (args == null || args.length == 0) {
            return pattern;
        }
        return MessageFormat.format(pattern, args);
    }

    private static ResourceBundle loadBundle(AppLanguage language) {
        return ResourceBundle.getBundle(BUNDLE_BASE_NAME, language.getLocale());
    }

    private static AppLanguage readLanguageFromSettings() {
        UiSettings settings = JsonStorage.read(SETTINGS_PATH, UiSettings.class, new UiSettings());
        return AppLanguage.fromCode(settings.language);
    }

    private static void saveLanguageToSettings(AppLanguage language) {
        UiSettings settings = new UiSettings();
        settings.language = language.getCode();
        JsonStorage.writeSafe(SETTINGS_PATH, settings);
    }

    private static class UiSettings {
        private String language = AppLanguage.AR.getCode();
    }
}
