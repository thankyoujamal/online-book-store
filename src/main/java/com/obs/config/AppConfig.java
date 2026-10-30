package com.obs.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Reads settings from app.properties. Any key can be overridden by an environment
 * variable of the same name in upper snake case, e.g. db.password -> DB_PASSWORD.
 */
public final class AppConfig {

    private final Properties props = new Properties();

    private AppConfig() {
    }

    public static AppConfig load(Path file) throws IOException {
        AppConfig config = new AppConfig();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                config.props.load(in);
            }
        }
        return config;
    }

    public String get(String key, String defaultValue) {
        String env = System.getenv(key.toUpperCase().replace('.', '_'));
        if (env != null && !env.isBlank()) {
            return env;
        }
        return props.getProperty(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Config key '" + key + "' must be a number, got: " + value);
        }
    }
}
