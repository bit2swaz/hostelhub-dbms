package com.hostelhub.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.hostelhub.support.OracleTestBase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** Maps real Oracle exceptions (raised by the schema, then rolled back) to plain-English messages. */
class OracleErrorMapperTest extends OracleTestBase {

    private static String mapped(String sql) throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                SQLException e = catchThrowableOfType(SQLException.class, ps::executeUpdate);
                assertThat((Throwable) e).as("statement should fail: %s", sql).isNotNull();
                return OracleErrorMapper.toMessage(e);
            } finally {
                c.rollback();
            }
        }
    }

    private static final String NAVEEN = "FROM student s, block b, warden w WHERE s.reg_no = '25BIT0633' "
            + "AND b.block_name = 'A Block' AND w.block_id = b.block_id";

    @Test
    void ora20001CapacityTriggerKeepsRoomAndRule() throws SQLException {
        assertThat(mapped("INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) "
                + "SELECT s.user_id, b.block_id, 'A-001', w.user_id, 'ACTIVE' " + NAVEEN))
                .isEqualTo("Room A-001 (A Block) is full (BR-1).");
    }

    @Test
    void ora00001UniqueIndexNamesBr2() throws SQLException {
        assertThat(mapped("INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) "
                + "SELECT s.user_id, b.block_id, 'A-002', w.user_id, 'ACTIVE' FROM student s, block b, warden w "
                + "WHERE s.reg_no = '25BCE1045' AND b.block_name = 'A Block' AND w.block_id = b.block_id"))
                .isEqualTo("This student already has an active allocation (BR-2).");
    }

    @Test
    void ora20003And20004TriggerMessages() throws SQLException {
        assertThat(mapped("INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) "
                + "SELECT s.user_id, b.block_id, 'B-002', w.user_id, 'ACTIVE' FROM student s, block b, warden w "
                + "WHERE s.reg_no = '25BIT0633' AND b.block_name = 'B Block' AND w.block_id = b.block_id"))
                .isEqualTo("Student gender does not match the block's gender (BR-3).");
        assertThat(mapped("INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) "
                + "SELECT s.user_id, b.block_id, 'A-002', w.user_id, 'ACTIVE' FROM student s, block b, warden w, block wb "
                + "WHERE s.reg_no = '25BIT0633' AND b.block_name = 'A Block' AND wb.block_name = 'C Block' AND w.block_id = wb.block_id"))
                .isEqualTo("A warden can only approve allocations in their own block (BR-4).");
    }

    @Test
    void ora20005StaffCategory() throws SQLException {
        assertThat(mapped("UPDATE complaint SET staff_id = (SELECT staff_id FROM staff WHERE full_name = 'Mahesh Gowda') "
                + "WHERE description = 'Tube light in the room flickers constantly.'"))
                .isEqualTo("Assigned staff must belong to the complaint's category.");
    }

    @Test
    void ora02290CheckNamesRuleAndConstraint() throws SQLException {
        assertThat(mapped("UPDATE complaint SET status = 'CLOSED' WHERE description = 'Bed frame is broken on one side.'"))
                .isEqualTo("Status must be OPEN, IN_PROGRESS or RESOLVED (BR-5, ck_complaint_status).");
        assertThat(mapped("UPDATE block SET num_floors = 0 WHERE block_name = 'A Block'"))
                .isEqualTo("Number of floors must be between 1 and 20 (ck_block_num_floors).");
        // Unlisted constraints still name the constraint.
        assertThat(mapped("UPDATE category SET sla_days = 0 WHERE category_name = 'Electrical'"))
                .isEqualTo("A value is not allowed (ck_category_sla_days).");
    }

    @Test
    void ora02291And02292() throws SQLException {
        assertThat(mapped("INSERT INTO staff (full_name, category_id) VALUES ('Nobody', -1)"))
                .isEqualTo("A referenced record does not exist (fk_staff_category).");
        assertThat(mapped("DELETE FROM room WHERE room_no = 'A-102'"))
                .isEqualTo("This room has allocation history and cannot be deleted (fk_allocation_room).");
        assertThat(mapped("DELETE FROM hostel_user WHERE email = 'kabir.sethi@hostelhub.test'"))
                .isEqualTo("This student has allocation history and cannot be deleted (fk_allocation_student).");
    }

    @Test
    void unknownErrorsShowCodeButNoInternals() {
        String msg = OracleErrorMapper.toMessage(new SQLException(
                "ORA-00942: table or view does not exist\nat oracle.jdbc...", "42000", 942));
        assertThat(msg).isEqualTo("Database error (ORA-00942). Please try again.");
        assertThat(OracleErrorMapper.toMessage(new SQLException("IO Error: refused", "08006", 17002)))
                .startsWith("Cannot connect to the database.");
    }
}
