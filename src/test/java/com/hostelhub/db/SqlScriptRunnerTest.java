package com.hostelhub.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SqlScriptRunnerTest {

    @Test
    void splitsSqlOnSemicolonAndPlsqlOnSlash() {
        String script = """
                -- comment
                SET DEFINE OFF
                PROMPT hello
                CREATE TABLE t (
                  id NUMBER
                );

                CREATE OR REPLACE TRIGGER trg
                BEFORE INSERT ON t
                BEGIN
                  NULL;
                END;
                /
                BEGIN
                  NULL;
                END;
                /
                INSERT INTO t VALUES (1);
                """;
        assertThat(SqlScriptRunner.parse(script)).containsExactly(
                "CREATE TABLE t (\n  id NUMBER\n)",
                "CREATE OR REPLACE TRIGGER trg\nBEFORE INSERT ON t\nBEGIN\n  NULL;\nEND;",
                "BEGIN\n  NULL;\nEND;",
                "INSERT INTO t VALUES (1)");
    }

    @Test
    void rejectsUnterminatedStatement() {
        assertThatThrownBy(() -> SqlScriptRunner.parse("SELECT 1 FROM dual"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesEveryProjectScript() throws Exception {
        assertThat(SqlScriptRunner.parse(Files.readString(Path.of("sql/01_schema.sql")))).hasSize(10);
        assertThat(SqlScriptRunner.parse(Files.readString(Path.of("sql/02_views.sql")))).hasSize(2);
        assertThat(SqlScriptRunner.parse(Files.readString(Path.of("sql/03_triggers.sql")))).hasSize(4);
        assertThat(SqlScriptRunner.parse(Files.readString(Path.of("sql/04_seed.sql")))).hasSize(206);
        assertThat(SqlScriptRunner.parse(Files.readString(Path.of("sql/99_drop.sql")))).hasSize(2);
    }
}
