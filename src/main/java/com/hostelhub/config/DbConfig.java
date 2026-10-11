package com.hostelhub.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;

/** Connection settings from {@code db.properties}, overridden by HOSTELHUB_DB_* environment variables. */
public record DbConfig(String url, String user, String password) {

    public static DbConfig load() {
        return load(System.getenv());
    }

    static DbConfig load(Map<String, String> env) {
        Properties props = new Properties();
        try (InputStream in = DbConfig.class.getResourceAsStream("/db.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read db.properties", e);
        }
        return new DbConfig(
                env.getOrDefault("HOSTELHUB_DB_URL", props.getProperty("db.url")),
                env.getOrDefault("HOSTELHUB_DB_USER", props.getProperty("db.user")),
                env.getOrDefault("HOSTELHUB_DB_PASSWORD", props.getProperty("db.password")));
    }

    @Override
    public String toString() {
        return "DbConfig[url=" + url + ", user=" + user + "]";
    }
}
