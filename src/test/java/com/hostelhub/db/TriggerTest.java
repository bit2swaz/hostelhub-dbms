package com.hostelhub.db;

import static com.hostelhub.db.ConstraintTest.exec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hostelhub.support.OracleTestBase;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Database-layer enforcement of BR-1, BR-3, BR-4 and the staff-category rule. Each test is rolled back. */
class TriggerTest extends OracleTestBase {

    private Connection c;

    @BeforeEach
    void open() throws SQLException {
        c = connect();
        c.setAutoCommit(false);
    }

    @AfterEach
    void rollback() throws SQLException {
        c.rollback();
        c.close();
    }

    /** Params: reg_no, block_name, room_no, block_name of the approving warden. */
    private static final String ALLOCATE = "INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) "
            + "SELECT s.user_id, b.block_id, ?, w.user_id, 'ACTIVE' FROM student s, block b, warden w, block wb "
            + "WHERE s.reg_no = ? AND b.block_name = ? AND wb.block_name = ? AND w.block_id = wb.block_id";

    private void allocate(String regNo, String block, String room, String wardenBlock) throws SQLException {
        exec(c, ALLOCATE, room, regNo, block, wardenBlock);
    }

    private void assertAllocateFails(String regNo, String block, String room, String wardenBlock, int code, String message) {
        assertAppError(() -> allocate(regNo, block, room, wardenBlock), code, message);
    }

    private void assertAppError(String sql, int code, String message) {
        assertAppError(() -> exec(c, sql), code, message);
    }

    private static void assertAppError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, int code, String message) {
        assertThatThrownBy(call)
                .isInstanceOf(SQLException.class)
                .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isEqualTo(code))
                .hasMessageContaining(message);
    }

    @Test
    void validAllocationPassesAllTriggers() throws SQLException {
        allocate("25BIT0633", "A Block", "A-201", "A Block");
        assertThat(queryLong(c, "SELECT occupancy FROM v_room_occupancy WHERE room_no = 'A-201'")).isEqualTo(3);
    }

    @Test
    void br1_insertIntoFullRoomRaises20001() {
        assertAllocateFails("25BIT0633", "A Block", "A-001", "A Block",
                20001, "ORA-20001: Room A-001 (A Block) is full (BR-1).");
    }

    @Test
    void br1_lastBedFillsThenNextInsertRaises20001() throws SQLException {
        // A-201 is a TRIPLE with 2 occupants: the third succeeds, a fourth is rejected.
        allocate("25BIT0633", "A Block", "A-201", "A Block");
        assertAllocateFails("25BCE1420", "A Block", "A-201", "A Block",
                20001, "Room A-201 (A Block) is full (BR-1).");
    }

    @Test
    void br1_updateMovingIntoFullRoomRaises20001() {
        // Statement-level trigger also fires on UPDATE: move Aditya Rao (A-003) into full A-001.
        assertAppError("UPDATE allocation SET room_no = 'A-001' WHERE status = 'ACTIVE' "
                        + "AND student_id = (SELECT user_id FROM student WHERE reg_no = '25BIT0415')",
                20001, "Room A-001 (A Block) is full (BR-1).");
    }

    @Test
    void br1_reactivatingIntoFullRoomRaises20001() throws SQLException {
        // Fill C-201 (DOUBLE, empty) then reactivate Kabir Sethi's old C-201 allocation.
        allocate("25BCE1420", "C Block", "C-201", "C Block");
        allocate("25BIT0633", "C Block", "C-201", "C Block");
        assertAppError("UPDATE allocation SET status = 'ACTIVE', end_date = NULL WHERE room_no = 'C-201' "
                        + "AND student_id = (SELECT user_id FROM student WHERE reg_no = '24BME0518')",
                20001, "Room C-201 (C Block) is full (BR-1).");
    }

    @Test
    void br3_femaleStudentIntoMaleBlockRaises20003() {
        assertAllocateFails("25BCE1498", "A Block", "A-002", "A Block",
                20003, "ORA-20003: Student gender does not match the block's gender (BR-3).");
    }

    @Test
    void br3_maleStudentIntoFemaleBlockRaises20003() {
        assertAllocateFails("25BIT0633", "B Block", "B-002", "B Block",
                20003, "(BR-3)");
    }

    @Test
    void br4_wardenOfAnotherBlockRaises20004() {
        assertAllocateFails("25BIT0633", "A Block", "A-002", "C Block",
                20004, "ORA-20004: A warden can only approve allocations in their own block (BR-4).");
    }

    @Test
    void staffCategoryMismatchOnInsertRaises20005() {
        assertAppError("INSERT INTO complaint (student_id, block_id, room_no, category_id, staff_id, description) "
                        + "SELECT a.student_id, a.block_id, a.room_no, c.category_id, st.staff_id, 'Fan is not working at all' "
                        + "FROM allocation a, student s, category c, staff st WHERE a.student_id = s.user_id AND a.status = 'ACTIVE' "
                        + "AND s.reg_no = '25BCE1045' AND c.category_name = 'Electrical' AND st.full_name = 'Mahesh Gowda'",
                20005, "ORA-20005: Assigned staff must belong to the complaint's category.");
    }

    @Test
    void staffCategoryMismatchOnUpdateRaises20005() {
        assertAppError("UPDATE complaint SET staff_id = (SELECT staff_id FROM staff WHERE full_name = 'Mahesh Gowda') "
                        + "WHERE description = 'Tube light in the room flickers constantly.'",
                20005, "Assigned staff must belong to the complaint's category.");
    }

    @Test
    void staffCategoryMatchIsAccepted() throws SQLException {
        exec(c, "UPDATE complaint SET staff_id = (SELECT staff_id FROM staff WHERE full_name = 'Joseph Thomas') "
                + "WHERE description = 'Washbasin drain is blocked and water does not flow out.'");
        assertThat(queryLong(c, "SELECT COUNT(*) FROM complaint WHERE staff_id IS NOT NULL "
                + "AND description = 'Washbasin drain is blocked and water does not flow out.'")).isEqualTo(1);
    }
}
