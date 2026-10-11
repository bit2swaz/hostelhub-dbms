package com.hostelhub.db;

import static com.hostelhub.db.ConstraintTest.exec;
import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.support.OracleTestBase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ViewTest extends OracleTestBase {

    private static Map<String, Long> roomCounts(Connection c, String sql) throws SQLException {
        Map<String, Long> m = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                m.put(rs.getLong(1) + "/" + rs.getString(2), rs.getLong(3));
            }
        }
        return m;
    }

    private static List<String> strings(Connection c, String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    @Test
    void occupancyEqualsActiveAllocationCountForEveryRoom() throws SQLException {
        try (Connection c = connect()) {
            Map<String, Long> view = roomCounts(c, "SELECT block_id, room_no, occupancy FROM v_room_occupancy");
            Map<String, Long> actual = roomCounts(c,
                    "SELECT r.block_id, r.room_no, (SELECT COUNT(*) FROM allocation a WHERE a.block_id = r.block_id "
                            + "AND a.room_no = r.room_no AND a.status = 'ACTIVE') FROM room r");
            assertThat(view).hasSize(36).isEqualTo(actual);
            assertThat(queryLong(c, "SELECT COUNT(*) FROM v_room_occupancy WHERE vacancies <> capacity - occupancy")).isZero();
            assertThat(queryLong(c, "SELECT SUM(occupancy) FROM v_room_occupancy")).isEqualTo(38);
        }
    }

    @Test
    void occupancyIsDerivedNotStored() throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String a103 = "SELECT occupancy FROM v_room_occupancy WHERE room_no = 'A-103'";
                assertThat(queryLong(c, a103)).isEqualTo(3);
                exec(c, "UPDATE allocation SET status = 'VACATED', end_date = SYSDATE WHERE status = 'ACTIVE' "
                        + "AND student_id = (SELECT user_id FROM student WHERE reg_no = '25BCE1045')");
                assertThat(queryLong(c, a103)).isEqualTo(2);
                assertThat(queryLong(c, "SELECT vacancies FROM v_room_occupancy WHERE room_no = 'A-103'")).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void everyBlockHasAnExactlyFullRoom() throws SQLException {
        try (Connection c = connect()) {
            assertThat(strings(c, "SELECT DISTINCT block_name FROM v_room_occupancy WHERE vacancies = 0 ORDER BY 1"))
                    .containsExactly("A Block", "B Block", "C Block");
        }
    }

    @Test
    void br6_seededOverdueComplaintsAppearInOverdueView() throws SQLException {
        try (Connection c = connect()) {
            assertThat(strings(c, "SELECT description FROM v_overdue_complaints")).containsExactlyInAnyOrder(
                    "Ceiling fan makes a loud noise and runs very slowly.",
                    "Wi-Fi keeps disconnecting every few minutes in the evening.",
                    "Tube light in the room flickers constantly.",
                    "Washbasin drain is blocked and water does not flow out.");
            assertThat(queryLong(c, "SELECT days_overdue FROM v_overdue_complaints "
                    + "WHERE description = 'Washbasin drain is blocked and water does not flow out.'")).isEqualTo(4);
            assertThat(strings(c, "SELECT DISTINCT block_name FROM v_overdue_complaints ORDER BY 1"))
                    .containsExactly("A Block", "B Block", "C Block");
        }
    }

    @Test
    void br6_resolvedAndWithinSlaComplaintsAreNotOverdue() throws SQLException {
        try (Connection c = connect()) {
            // Resolved (even though raised 20 days ago) and within SLA are excluded.
            assertThat(queryLong(c, "SELECT COUNT(*) FROM v_overdue_complaints WHERE status = 'RESOLVED'")).isZero();
            assertThat(queryLong(c, "SELECT COUNT(*) FROM v_overdue_complaints v JOIN complaint cp "
                    + "ON cp.complaint_id = v.complaint_id WHERE SYSDATE - cp.raised_on <= v.sla_days")).isZero();
            assertThat(queryLong(c, "SELECT COUNT(*) FROM complaint WHERE status <> 'RESOLVED'")).isEqualTo(10);
            assertThat(queryLong(c, "SELECT COUNT(*) FROM v_overdue_complaints")).isEqualTo(4);
        }
    }

    @Test
    void br6_complaintBecomesOverdueOnlyAfterSlaDays() throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                String desc = "Cupboard door hinge is broken and the door will not close.";
                String inView = "SELECT COUNT(*) FROM v_overdue_complaints WHERE description = ?";
                // Carpentry SLA is 5 days.
                exec(c, "UPDATE complaint SET raised_on = SYSDATE - 5 + 1/24 WHERE description = ?", desc);
                assertThat(queryLong(c, inView, desc)).isZero();
                exec(c, "UPDATE complaint SET raised_on = SYSDATE - 5 - 1/24 WHERE description = ?", desc);
                assertThat(queryLong(c, inView, desc)).isEqualTo(1);
            } finally {
                c.rollback();
            }
        }
    }
}
