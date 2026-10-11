package com.hostelhub.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Runs the SQL*Plus scripts in {@code sql/} over JDBC, following SQL*Plus terminator rules:
 * a SQL statement ends with {@code ;} at the end of a line, a PL/SQL block ends with
 * {@code /} on its own line. SQL*Plus commands (SET, PROMPT, COLUMN, ...) and comment lines
 * between statements are skipped.
 */
public final class SqlScriptRunner {

    private static final Pattern PLSQL_START = Pattern.compile(
            "^(DECLARE|BEGIN|CREATE\\s+(OR\\s+REPLACE\\s+)?(TRIGGER|PROCEDURE|FUNCTION|PACKAGE|TYPE)\\b).*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final List<String> SQLPLUS_COMMANDS =
            List.of("SET", "PROMPT", "COLUMN", "SPOOL", "WHENEVER", "EXIT", "REM", "REMARK");

    private SqlScriptRunner() {
    }

    public static void run(Connection connection, Path script) throws IOException, SQLException {
        for (String sql : parse(Files.readString(script))) {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.execute();
            } catch (SQLException e) {
                throw new SQLException(script.getFileName() + ": " + e.getMessage() + "\n--- statement ---\n" + sql,
                        e.getSQLState(), e.getErrorCode(), e);
            }
        }
    }

    public static List<String> parse(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        boolean plsql = false;
        for (String line : script.split("\\R")) {
            String trimmed = line.trim();
            if (buffer.isEmpty()) {
                if (trimmed.isEmpty() || trimmed.startsWith("--") || isSqlPlusCommand(trimmed)) {
                    continue;
                }
                plsql = PLSQL_START.matcher(trimmed).matches();
            }
            if (trimmed.equals("/")) {
                if (!buffer.isEmpty()) {
                    statements.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                continue;
            }
            if (!plsql && trimmed.endsWith(";")) {
                buffer.append(line, 0, line.lastIndexOf(';'));
                statements.add(buffer.toString().trim());
                buffer.setLength(0);
                continue;
            }
            buffer.append(line).append('\n');
        }
        if (!buffer.toString().isBlank()) {
            throw new IllegalArgumentException("Unterminated statement at end of script:\n" + buffer);
        }
        return statements;
    }

    private static boolean isSqlPlusCommand(String line) {
        String first = line.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        return SQLPLUS_COMMANDS.contains(first);
    }
}
