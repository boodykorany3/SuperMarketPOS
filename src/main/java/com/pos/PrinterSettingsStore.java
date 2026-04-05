package com.pos;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PrinterSettingsStore {

    private static final Path FILE = Paths.get("printer-settings.json");

    public static PrinterSettings load() {
        Type type = PrinterSettings.class;
        return JsonStorage.read(FILE, type, new PrinterSettings());
    }

    public static void save(PrinterSettings settings) {
        JsonStorage.write(FILE, settings == null ? new PrinterSettings() : settings);
    }
}
