package com.hostelhub.support;

import com.hostelhub.config.DbConfig;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.db.SqlScriptRunner;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * One Oracle container shared by every test class in the JVM. On first use it starts the
 * container (image from -Dhostelhub.oracle.image) and runs sql/01-04 through SqlScriptRunner.
 * The sql/ folder is also copied to /tmp/sql in the container for the SQL*Plus test.
 */
public abstract class OracleTestBase {

    public static final String DEFAULT_IMAGE = "gvenzl/oracle-xe:21-slim-faststart";
    public static final Path SQL_DIR = Path.of("sql");
    public static final String SEED_PASSWORD = "Hostel@123";

    protected static final JdbcDatabaseContainer<?> ORACLE;
    protected static final ConnectionFactory DB;

    private static final List<String> DELETE_ALL = List.of(
            "DELETE FROM complaint",
            "DELETE FROM allocation",
            "DELETE FROM staff",
            "DELETE FROM category",
            "DELETE FROM room",
            "DELETE FROM warden",
            "DELETE FROM student",
            "DELETE FROM hostel_user",
            "DELETE FROM block");

    static {
        String image = System.getProperty("hostelhub.oracle.image", DEFAULT_IMAGE);
        ORACLE = createContainer(image)
                .withUsername("hhtest")
                .withPassword("hhtest")
                .withCopyFileToContainer(MountableFile.forHostPath(SQL_DIR), "/tmp/sql")
                // First start of a fresh container can take minutes on a busy laptop or CI runner.
                .withStartupTimeout(java.time.Duration.ofMinutes(5));
        ORACLE.start();
        DB = new ConnectionFactory(new DbConfig(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword()));
        try (Connection c = DB.open()) {
            for (String script : List.of("01_schema.sql", "02_views.sql", "03_triggers.sql", "04_seed.sql")) {
                SqlScriptRunner.run(c, SQL_DIR.resolve(script));
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static JdbcDatabaseContainer<?> createContainer(String image) {
        DockerImageName name = DockerImageName.parse(image);
        if (name.getRepository().endsWith("oracle-free")) {
            return new org.testcontainers.oracle.OracleContainer(name.asCompatibleSubstituteFor("gvenzl/oracle-free"));
        }
        return new org.testcontainers.containers.OracleContainer(name.asCompatibleSubstituteFor("gvenzl/oracle-xe"));
    }

    protected static Connection connect() throws SQLException {
        return DB.open();
    }

    /** Deletes every row and re-runs sql/04_seed.sql, for tests that commit. */
    protected static void resetData() throws Exception {
        try (Connection c = DB.open()) {
            for (String sql : DELETE_ALL) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.executeUpdate();
                }
            }
            SqlScriptRunner.run(c, SQL_DIR.resolve("04_seed.sql"));
        }
    }

    /** Runs a single-value query; parameters are bound in order. */
    protected static long queryLong(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No row for: " + sql);
                }
                return rs.getLong(1);
            }
        }
    }

    /** Today according to the database (SYSDATE); the container may run in a different time zone. */
    protected static java.time.LocalDate dbToday() throws SQLException {
        try (Connection c = DB.open(); PreparedStatement ps = c.prepareStatement("SELECT SYSDATE FROM dual");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getTimestamp(1).toLocalDateTime().toLocalDate();
        }
    }

    protected static long queryLong(String sql, Object... params) throws SQLException {
        try (Connection c = DB.open()) {
            return queryLong(c, sql, params);
        }
    }
}
