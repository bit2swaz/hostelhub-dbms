package com.hostelhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.hostelhub.model.Complaint;
import com.hostelhub.model.ComplaintStatus;
import com.hostelhub.support.ServiceTestBase;
import com.hostelhub.util.Session;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/**
 * PRD section 7 enforcement matrix: every business rule is rejected by the service layer
 * (exact message, no SQLException cause = the database was never asked) AND by the database
 * on its own (exact ORA code and message, writing directly over JDBC past the service).
 * BR-6 is a derived flag, so it is verified at both layers rather than rejected.
 */
class BusinessRulesTest extends ServiceTestBase {

    /** Runs a statement directly against the database and returns the rejection. Rolled back. */
    private static SQLException dbRejects(String sql, Object... params) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                SQLException e = catchThrowableOfType(SQLException.class, ps::executeUpdate);
                assertThat((Throwable) e).as("database should reject: %s", sql).isNotNull();
                return e;
            } finally {
                c.rollback();
            }
        }
    }

    /** Params: room_no, reg_no, block_name, approving warden's block_name. */
    private static final String INSERT_ALLOCATION = "INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) "
            + "SELECT s.user_id, b.block_id, ?, w.user_id, 'ACTIVE' FROM student s, block b, warden w, block wb "
            + "WHERE s.reg_no = ? AND b.block_name = ? AND wb.block_name = ? AND w.block_id = wb.block_id";

    // ---------- BR-1: occupancy never exceeds capacity ----------

    @Test
    void br1_service_rejectsFullRoom() throws Exception {
        long naveen = studentId("25BIT0633");
        assertServiceRejects(() -> allocations.allocate(wardenA(), naveen, "A-001"), "Room A-001 (A Block) is full (BR-1).");
    }

    @Test
    void br1_database_trgAllocCapacity() throws Exception {
        SQLException e = dbRejects(INSERT_ALLOCATION, "A-001", "25BIT0633", "A Block", "A Block");
        assertThat(e.getErrorCode()).isEqualTo(20001);
        assertThat((Throwable) e).hasMessageContaining("ORA-20001: Room A-001 (A Block) is full (BR-1).");
    }

    // ---------- BR-2: at most one ACTIVE allocation per student ----------

    @Test
    void br2_service_rejectsSecondActiveAllocation() throws Exception {
        long arjun = studentId("25BCE1045");
        assertServiceRejects(() -> allocations.allocate(wardenA(), arjun, "A-002"),
                "Student 25BCE1045 already has an active allocation (BR-2).");
    }

    @Test
    void br2_database_uxAllocOneActive() throws Exception {
        SQLException e = dbRejects(INSERT_ALLOCATION, "A-002", "25BCE1045", "A Block", "A Block");
        assertThat(e.getErrorCode()).isEqualTo(1);
        assertThat((Throwable) e).hasMessageContaining("ORA-00001").hasMessageContaining(".UX_ALLOC_ONE_ACTIVE)");
    }

    // ---------- BR-3: student gender matches block gender_type ----------

    @Test
    void br3_service_rejectsGenderMismatch() throws Exception {
        long naveen = studentId("25BIT0633");
        assertServiceRejects(() -> allocations.allocate(wardenB(), naveen, "B-002"),
                "Student gender does not match the block's gender (BR-3).");
        // The picker never offers a mismatched student in the first place.
        assertThat(allocations.eligibleStudents(wardenB())).allMatch(s -> s.gender().equals("F"));
    }

    @Test
    void br3_database_trgAllocGender() throws Exception {
        SQLException e = dbRejects(INSERT_ALLOCATION, "B-002", "25BIT0633", "B Block", "B Block");
        assertThat(e.getErrorCode()).isEqualTo(20003);
        assertThat((Throwable) e).hasMessageContaining("ORA-20003: Student gender does not match the block's gender (BR-3).");
    }

    // ---------- BR-4: a warden manages exactly one block and acts only on it ----------

    @Test
    void br4_service_rejectsActionsOnAnotherBlock() throws Exception {
        long arjunAlloc = activeAllocId("25BCE1045");
        long aComplaint = complaintId("Wi-Fi keeps disconnecting every few minutes in the evening.");
        long naveen = studentId("25BIT0633");
        Session b = wardenB();
        assertServiceRejects(() -> allocations.vacate(b, arjunAlloc), "You can only manage allocations in your own block (BR-4).");
        assertServiceRejects(() -> allocations.transfer(b, arjunAlloc, "B-002"),
                "You can only manage allocations in your own block (BR-4).");
        assertServiceRejects(() -> complaints.assignStaff(b, aComplaint, staffId("Arun Prakash")),
                "You can only manage complaints in your own block (BR-4).");
        assertServiceRejects(() -> complaints.updateStatus(b, aComplaint, ComplaintStatus.IN_PROGRESS),
                "You can only manage complaints in your own block (BR-4).");
        assertServiceRejects(() -> allocations.allocate(b, naveen, "A-002"), "Room A-002 was not found in your block.");
        assertServiceRejects(() -> allocations.history(login("arjun.nair@hostelhub.test"), null), "Only wardens can do this.");
    }

    @Test
    void br4_database_trgAllocWardenBlockAndUqWardenBlock() throws Exception {
        SQLException e = dbRejects(INSERT_ALLOCATION, "A-002", "25BIT0633", "A Block", "C Block");
        assertThat(e.getErrorCode()).isEqualTo(20004);
        assertThat((Throwable) e).hasMessageContaining("ORA-20004: A warden can only approve allocations in their own block (BR-4).");

        SQLException twoWardens = dbRejects("UPDATE warden SET block_id = ? WHERE block_id = ?",
                blockId("A Block"), blockId("B Block"));
        assertThat(twoWardens.getErrorCode()).isEqualTo(1);
        assertThat((Throwable) twoWardens).hasMessageContaining(".UQ_WARDEN_BLOCK)");
    }

    // ---------- BR-5: status in {OPEN, IN_PROGRESS, RESOLVED}, forward only; resolved_on >= raised_on ----------

    @Test
    void br5_service_forwardOnlyStateMachine() throws Exception {
        Session w = wardenA();
        long inProgress = complaintId("Ceiling fan makes a loud noise and runs very slowly.");
        long open = complaintId("Wi-Fi keeps disconnecting every few minutes in the evening.");
        long resolved = complaintId("Bathroom tap is leaking continuously.");
        String forwardOnly = "Status can only move forward one step: OPEN -> IN_PROGRESS -> RESOLVED (BR-5).";
        assertServiceRejects(() -> complaints.updateStatus(w, inProgress, ComplaintStatus.OPEN), forwardOnly);
        assertServiceRejects(() -> complaints.updateStatus(w, inProgress, ComplaintStatus.IN_PROGRESS), forwardOnly);
        assertServiceRejects(() -> complaints.updateStatus(w, open, ComplaintStatus.RESOLVED), forwardOnly);
        assertServiceRejects(() -> complaints.updateStatus(w, resolved, ComplaintStatus.IN_PROGRESS),
                "Complaint #" + resolved + " is already resolved (BR-5).");
    }

    @Test
    void br5_database_ckComplaintStatusAndDates() throws Exception {
        SQLException badStatus = dbRejects("UPDATE complaint SET status = 'CLOSED' WHERE description = ?",
                "Bed frame is broken on one side.");
        assertThat(badStatus.getErrorCode()).isEqualTo(2290);
        assertThat((Throwable) badStatus).hasMessageContaining(".CK_COMPLAINT_STATUS)");

        SQLException badDates = dbRejects("UPDATE complaint SET resolved_on = raised_on - 1 WHERE description = ?",
                "Bathroom tap is leaking continuously.");
        assertThat(badDates.getErrorCode()).isEqualTo(2290);
        assertThat((Throwable) badDates).hasMessageContaining(".CK_COMPLAINT_DATES)");

        SQLException resolvedWithoutDate = dbRejects("UPDATE complaint SET status = 'RESOLVED' WHERE description = ?",
                "Bed frame is broken on one side.");
        assertThat(resolvedWithoutDate.getErrorCode()).isEqualTo(2290);
        assertThat((Throwable) resolvedWithoutDate).hasMessageContaining(".CK_COMPLAINT_RESOLVED)");
    }

    // ---------- BR-6: overdue once open longer than sla_days (verified, not rejected) ----------

    @Test
    void br6_service_overdueFlagMatchesSla() {
        assertThat(complaints.myComplaints(login("arjun.nair@hostelhub.test")))
                .extracting(Complaint::categoryName, Complaint::overdue)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("Electrical", true),
                        org.assertj.core.groups.Tuple.tuple("Internet", true));
        // Carpentry raised 1 day ago with a 5-day SLA is not overdue.
        assertThat(complaints.myComplaints(login("varun.menon@hostelhub.test")))
                .singleElement().satisfies(c -> assertThat(c.overdue()).isFalse());
        assertThat(reports.overdueComplaints(wardenC())).extracting(Complaint::description)
                .containsExactly("Washbasin drain is blocked and water does not flow out.");
    }

    @Test
    void br6_database_vOverdueComplaints() throws Exception {
        assertThat(queryLong("SELECT COUNT(*) FROM v_overdue_complaints")).isEqualTo(4);
        assertThat(queryLong("SELECT COUNT(*) FROM v_overdue_complaints WHERE status = 'RESOLVED' "
                + "OR days_overdue < 0")).isZero();
    }
}
