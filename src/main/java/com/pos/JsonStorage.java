package com.pos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonStorage() {}

    public static <T> T read(Path path, java.lang.reflect.Type type, T fallback) {
        try {
            if (!Files.exists(path)) {
                return fallback;
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                T data = GSON.fromJson(reader, type);
                return data == null ? fallback : data;
            }
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static void write(Path path, Object data) {
        writeSafe(path, data);
    }

    public static boolean writeSafe(Path path, Object data) {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(data, writer);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
