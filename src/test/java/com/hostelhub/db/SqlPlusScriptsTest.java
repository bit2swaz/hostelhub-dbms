package com.hostelhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.support.OracleTestBase;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;

/**
 * Runs the unmodified sql/ scripts in real SQL*Plus inside the Oracle container, as the README
 * tells a student to: 00 as SYSTEM, then 01-05 as hostelhub, then 99 twice, then 01-04 again.
 * Uses a separate 'hostelhub' schema, so it does not touch the schema the other tests use.
 */
class SqlPlusScriptsTest extends OracleTestBase {

    private static final Pattern ERROR = Pattern.compile("(ORA|SP2|PLS)-\\d{4,5}|Warning: .*compilation errors");
    private static final Pattern EXPECTED = Pattern.compile("^-- Expected: (ORA-\\d{5}) \\((\\w+)\\)", Pattern.MULTILINE);
    private static final Pattern ACTUAL = Pattern.compile("^(ORA-\\d{5}): .*", Pattern.MULTILINE);

    private static String sqlplus(String login, String script) throws Exception {
        String connect = login + "@//localhost:1521/" + ORACLE.getDatabaseName();
        ExecResult r = ORACLE.execInContainer("bash", "-c",
                "cd /tmp/sql && echo exit | sqlplus -S -L '" + connect + "' @" + script);
        assertThat(r.getExitCode()).as("sqlplus exit code for %s:%n%s", script, r.getStdout()).isZero();
        return r.getStdout();
    }

    private static void assertClean(String login, String script) throws Exception {
        String out = sqlplus(login, script);
        assertThat(ERROR.matcher(out).find()).as("errors in %s:%n%s", script, out).isFalse();
    }

    @Test
    void scriptsRunCleanInSqlPlusInOrderAndAfterDrop() throws Exception {
        String system = "system/" + ORACLE.getPassword();
        String app = "hostelhub/hostelhub";

        assertClean(system, "00_create_user.sql");
        for (String s : List.of("01_schema.sql", "02_views.sql", "03_triggers.sql", "04_seed.sql")) {
            assertClean(app, s);
        }
        String demo = sqlplus(app, "05_queries.sql");
        assertClean(app, "99_drop.sql");
        assertClean(app, "99_drop.sql"); // safe to re-run
        for (String s : List.of("01_schema.sql", "02_views.sql", "03_triggers.sql", "04_seed.sql")) {
            assertClean(app, s);
        }

        // 05: every deliberate violation fails with exactly the ORA code and constraint in its comment.
        String script = Files.readString(SQL_DIR.resolve("05_queries.sql"));
        List<String> expected = new ArrayList<>();
        Matcher e = EXPECTED.matcher(script);
        while (e.find()) {
            expected.add(e.group(1) + " " + e.group(2).toUpperCase());
        }
        List<String> actual = new ArrayList<>();
        Matcher a = ACTUAL.matcher(demo);
        while (a.find()) {
            String code = a.group(1);
            if (code.equals("ORA-06512") || code.equals("ORA-04088") || code.equals("ORA-03301")) {
                continue; // detail lines: PL/SQL stack under a trigger error; 23ai's "(ORA-00001 details)"
            }
            String line = a.group();
            String constraint = line.contains("ORA-2000")
                    ? triggerFor(demo, a.end())
                    : line.replaceAll(".*\\.(\\w+)\\).*", "$1");
            actual.add(code + " " + constraint);
        }
        assertThat(expected).hasSize(17);
        assertThat(actual).as("05_queries.sql output:%n%s", demo).containsExactlyElementsOf(expected);
        assertThat(demo).contains("Rollback complete.").doesNotContain("SP2-");
    }

    /** For an application error, the trigger name is on the ORA-04088 line that follows it. */
    private static String triggerFor(String output, int from) {
        Matcher m = Pattern.compile("trigger 'HOSTELHUB\\.(\\w+)'").matcher(output);
        return m.find(from) ? m.group(1) : "?";
    }
}
