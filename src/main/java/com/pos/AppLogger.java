package com.pos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class AppLogger {
    private static final String BASE_LOGGER_NAME = "com.pos";
    private static final Logger BASE_LOGGER = Logger.getLogger(BASE_LOGGER_NAME);
    private static volatile boolean configured = false;

    private AppLogger() {
    }

    public static Logger getLogger(Class<?> type) {
        configureOnce();
        return Logger.getLogger(type.getName());
    }

    private static synchronized void configureOnce() {
        if (configured) {
            return;
        }
        try {
            Path logDir = Paths.get("logs");
            Files.createDirectories(logDir);

            FileHandler fileHandler = new FileHandler("logs/supermarket-pos.%g.log", 1024 * 1024, 5, true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new OneLineFormatter());

            BASE_LOGGER.addHandler(fileHandler);
            BASE_LOGGER.setLevel(Level.ALL);
        } catch (IOException e) {
            System.err.println("Could not initialize file logger: " + e.getMessage());
        } finally {
            configured = true;
        }
    }

    private static final class OneLineFormatter extends Formatter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public String format(LogRecord record) {
            String timestamp = dateFormat.format(new Date(record.getMillis()));
            String loggerName = record.getLoggerName() == null ? BASE_LOGGER_NAME : record.getLoggerName();
            String message = formatMessage(record);
            StringBuilder out = new StringBuilder();
            out.append(timestamp)
                    .append(" [").append(record.getLevel()).append("] ")
                    .append(loggerName)
                    .append(" - ")
                    .append(message)
                    .append(System.lineSeparator());
            if (record.getThrown() != null) {
                Throwable t = record.getThrown();
                out.append("  -> ").append(t.getClass().getSimpleName()).append(": ").append(t.getMessage()).append(System.lineSeparator());
            }
            return out.toString();
        }
    }
}
