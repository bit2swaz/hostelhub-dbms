package com.hostelhub.db;

import com.hostelhub.config.DbConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens a new JDBC connection per call. Callers close it with try-with-resources. */
// No connection pool: one desktop user runs a few queries per action. Add a pool if that changes.
public final class ConnectionFactory {

    private final DbConfig config;

    public ConnectionFactory(DbConfig config) {
        this.config = config;
    }

    public Connection open() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }
}
