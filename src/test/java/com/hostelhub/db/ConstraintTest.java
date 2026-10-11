package com.hostelhub.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hostelhub.support.OracleTestBase;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * One test per named constraint (PK, FK, UNIQUE, CHECK, NOT NULL) and the BR-2 unique index.
 * Each case runs setup statements, then one violating statement, and asserts the exact ORA code
 * and the constraint (or column) named in the message. Every case is rolled back.
 */
class ConstraintTest extends OracleTestBase {

    record Case(String constraint, int oraCode, String messageFragment, String... sql) {
        @Override
        public String toString() {
            return constraint + " -> ORA-" + String.format("%05d", oraCode);
        }
    }

    static Case named(String constraint, int code, String... sql) {
        return new Case(constraint, code, "." + constraint.toUpperCase() + ")", sql);
    }

    static Case notNull(String constraint, String table, String column, String sql) {
        return new Case(constraint, 1407, "\"" + table + "\".\"" + column + "\"", sql);
    }

    // Natural-key subqueries used by many cases (all from 04_seed.sql).
    private static final String NAVEEN_INTO_A002 =
            "FROM student s, block b, warden w WHERE s.reg_no = '25BIT0633' AND b.block_name = 'A Block' AND w.block_id = b.block_id";
    private static final String ARJUN_COMPLAINT =
            "FROM allocation a, student s, category c WHERE a.student_id = s.user_id AND a.status = 'ACTIVE' "
                    + "AND s.reg_no = '25BCE1045' AND c.category_name = 'Carpentry'";
    private static final String NEW_BLOCK =
            "INSERT INTO block (block_name, gender_type, num_floors) VALUES ('Z Block', 'M', 2)";

    static Stream<Case> constraintCases() {
        return Stream.of(
                // HOSTEL_USER
                named("pk_hostel_user", 1,
                        "INSERT INTO hostel_user (user_id, full_name, email, password, user_role) SELECT user_id, 'Dup Id', 'dup.id@hostelhub.test', 'x', 'STUDENT' FROM hostel_user WHERE email = 'warden.a@hostelhub.test'"),
                named("uq_hostel_user_email", 1,
                        "INSERT INTO hostel_user (full_name, email, password, user_role) VALUES ('Dup Email', 'warden.a@hostelhub.test', 'x', 'WARDEN')"),
                named("ck_hostel_user_email", 2290,
                        "INSERT INTO hostel_user (full_name, email, password, user_role) VALUES ('Bad Email', 'not-an-email', 'x', 'STUDENT')"),
                named("ck_hostel_user_phone", 2290,
                        "UPDATE hostel_user SET phone = '98400' WHERE email = 'warden.a@hostelhub.test'"),
                named("ck_hostel_user_role", 2290,
                        "UPDATE hostel_user SET user_role = 'ADMIN' WHERE email = 'warden.a@hostelhub.test'"),
                // STUDENT
                named("pk_student", 1,
                        "INSERT INTO student (user_id, reg_no, gender, programme, year_of_study) SELECT user_id, '25BCE9999', 'M', 'B.Tech', 1 FROM student WHERE reg_no = '25BCE1045'"),
                named("fk_student_user", 2291,
                        "INSERT INTO student (user_id, reg_no, gender, programme, year_of_study) VALUES (-1, '25BCE9999', 'M', 'B.Tech', 1)"),
                named("uq_student_reg_no", 1,
                        "UPDATE student SET reg_no = '25BCE1045' WHERE reg_no = '25BCE1189'"),
                named("ck_student_reg_no", 2290,
                        "UPDATE student SET reg_no = '25bce1189' WHERE reg_no = '25BCE1189'"),
                named("ck_student_gender", 2290,
                        "UPDATE student SET gender = 'X' WHERE reg_no = '25BCE1189'"),
                named("ck_student_year_of_study", 2290,
                        "UPDATE student SET year_of_study = 6 WHERE reg_no = '25BCE1189'"),
                // BLOCK
                named("pk_block", 1,
                        "INSERT INTO block (block_id, block_name, gender_type, num_floors) SELECT block_id, 'Z Block', 'M', 2 FROM block WHERE block_name = 'A Block'"),
                named("uq_block_name", 1,
                        "INSERT INTO block (block_name, gender_type, num_floors) VALUES ('A Block', 'M', 2)"),
                named("ck_block_gender_type", 2290,
                        "INSERT INTO block (block_name, gender_type, num_floors) VALUES ('Z Block', 'X', 2)"),
                named("ck_block_num_floors", 2290,
                        "INSERT INTO block (block_name, gender_type, num_floors) VALUES ('Z Block', 'M', 21)"),
                // WARDEN
                named("pk_warden", 1, NEW_BLOCK,
                        "INSERT INTO warden (user_id, block_id) SELECT w.user_id, z.block_id FROM warden w, block a, block z WHERE w.block_id = a.block_id AND a.block_name = 'A Block' AND z.block_name = 'Z Block'"),
                named("fk_warden_user", 2291, NEW_BLOCK,
                        "INSERT INTO warden (user_id, block_id) SELECT -1, block_id FROM block WHERE block_name = 'Z Block'"),
                named("uq_warden_block", 1,
                        "UPDATE warden SET block_id = (SELECT block_id FROM block WHERE block_name = 'A Block') WHERE block_id = (SELECT block_id FROM block WHERE block_name = 'B Block')"),
                named("fk_warden_block", 2291,
                        "UPDATE warden SET block_id = -1 WHERE block_id = (SELECT block_id FROM block WHERE block_name = 'A Block')"),
                // ROOM
                named("pk_room", 1,
                        "INSERT INTO room (block_id, room_no, floor_no, room_type, capacity) SELECT block_id, 'A-001', 0, 'SINGLE', 1 FROM block WHERE block_name = 'A Block'"),
                named("fk_room_block", 2291,
                        "INSERT INTO room (block_id, room_no, floor_no, room_type, capacity) VALUES (-1, 'X-001', 0, 'SINGLE', 1)"),
                named("ck_room_capacity_type", 2290,
                        "INSERT INTO room (block_id, room_no, floor_no, room_type, capacity) SELECT block_id, 'A-304', 3, 'DOUBLE', 3 FROM block WHERE block_name = 'A Block'"),
                named("ck_room_floor_no", 2290,
                        "INSERT INTO room (block_id, room_no, floor_no, room_type, capacity) SELECT block_id, 'A-304', -1, 'SINGLE', 1 FROM block WHERE block_name = 'A Block'"),
                named("ck_room_room_type", 2290,
                        "INSERT INTO room (block_id, room_no, floor_no, room_type, capacity) SELECT block_id, 'A-304', 3, 'SUITE', 1 FROM block WHERE block_name = 'A Block'"),
                named("ck_room_capacity", 2290,
                        "INSERT INTO room (block_id, room_no, floor_no, room_type, capacity) SELECT block_id, 'A-304', 3, 'QUAD', 5 FROM block WHERE block_name = 'A Block'"),
                // ALLOCATION
                named("pk_allocation", 1,
                        "INSERT INTO allocation (alloc_id, student_id, block_id, room_no, approved_by, start_date, end_date, status) SELECT (SELECT MIN(alloc_id) FROM allocation), s.user_id, b.block_id, 'A-002', w.user_id, SYSDATE - 2, SYSDATE - 1, 'VACATED' " + NAVEEN_INTO_A002),
                named("fk_allocation_student", 2291,
                        "INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) SELECT -1, b.block_id, 'A-002', w.user_id, 'ACTIVE' FROM block b, warden w WHERE b.block_name = 'A Block' AND w.block_id = b.block_id"),
                named("fk_allocation_room", 2291,
                        "INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) SELECT s.user_id, b.block_id, 'A-999', w.user_id, 'ACTIVE' " + NAVEEN_INTO_A002),
                named("fk_allocation_warden", 2291,
                        "INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) SELECT s.user_id, b.block_id, 'A-002', s.user_id, 'ACTIVE' " + NAVEEN_INTO_A002),
                named("ck_allocation_status_end", 2290,
                        "INSERT INTO allocation (student_id, block_id, room_no, approved_by, end_date, status) SELECT s.user_id, b.block_id, 'A-002', w.user_id, SYSDATE, 'ACTIVE' " + NAVEEN_INTO_A002),
                named("ck_allocation_status", 2290,
                        "UPDATE allocation SET status = 'DONE' WHERE status = 'ACTIVE' AND student_id = (SELECT user_id FROM student WHERE reg_no = '25BCE1045')"),
                named("ck_allocation_dates", 2290,
                        "INSERT INTO allocation (student_id, block_id, room_no, approved_by, start_date, end_date, status) SELECT s.user_id, b.block_id, 'A-002', w.user_id, SYSDATE, SYSDATE - 1, 'VACATED' " + NAVEEN_INTO_A002),
                named("ux_alloc_one_active", 1,
                        "INSERT INTO allocation (student_id, block_id, room_no, approved_by, status) SELECT s.user_id, b.block_id, 'A-002', w.user_id, 'ACTIVE' FROM student s, block b, warden w WHERE s.reg_no = '25BCE1045' AND b.block_name = 'A Block' AND w.block_id = b.block_id"),
                // CATEGORY
                named("pk_category", 1,
                        "INSERT INTO category (category_id, category_name, sla_days) SELECT category_id, 'Laundry', 3 FROM category WHERE category_name = 'Electrical'"),
                named("uq_category_name", 1,
                        "INSERT INTO category (category_name, sla_days) VALUES ('Electrical', 3)"),
                named("ck_category_sla_days", 2290,
                        "INSERT INTO category (category_name, sla_days) VALUES ('Laundry', 0)"),
                // STAFF
                named("pk_staff", 1,
                        "INSERT INTO staff (staff_id, full_name, category_id) SELECT staff_id, 'Dup Staff', category_id FROM staff WHERE full_name = 'Ravi Kumar'"),
                named("fk_staff_category", 2291,
                        "INSERT INTO staff (full_name, category_id) VALUES ('No Category', -1)"),
                named("ck_staff_phone", 2290,
                        "UPDATE staff SET phone = '12ab' WHERE full_name = 'Ravi Kumar'"),
                // COMPLAINT
                named("pk_complaint", 1,
                        "INSERT INTO complaint (complaint_id, student_id, block_id, room_no, category_id, description) SELECT (SELECT MIN(complaint_id) FROM complaint), a.student_id, a.block_id, a.room_no, c.category_id, 'Duplicate complaint id' " + ARJUN_COMPLAINT),
                named("fk_complaint_student", 2291,
                        "INSERT INTO complaint (student_id, block_id, room_no, category_id, description) SELECT -1, a.block_id, a.room_no, c.category_id, 'Student does not exist' " + ARJUN_COMPLAINT),
                named("fk_complaint_room", 2291,
                        "INSERT INTO complaint (student_id, block_id, room_no, category_id, description) SELECT a.student_id, a.block_id, 'A-999', c.category_id, 'Room does not exist' " + ARJUN_COMPLAINT),
                named("fk_complaint_category", 2291,
                        "INSERT INTO complaint (student_id, block_id, room_no, category_id, description) SELECT a.student_id, a.block_id, a.room_no, -1, 'Category does not exist' " + ARJUN_COMPLAINT),
                named("fk_complaint_staff", 2291,
                        "INSERT INTO complaint (student_id, block_id, room_no, category_id, staff_id, description) SELECT a.student_id, a.block_id, a.room_no, c.category_id, -1, 'Staff does not exist' " + ARJUN_COMPLAINT),
                named("ck_complaint_description", 2290,
                        "INSERT INTO complaint (student_id, block_id, room_no, category_id, description) SELECT a.student_id, a.block_id, a.room_no, c.category_id, 'Too short' " + ARJUN_COMPLAINT),
                named("ck_complaint_status", 2290,
                        "UPDATE complaint SET status = 'CLOSED' WHERE description = 'Bed frame is broken on one side.'"),
                named("ck_complaint_resolved", 2290,
                        "UPDATE complaint SET status = 'RESOLVED' WHERE description = 'Bed frame is broken on one side.'"),
                named("ck_complaint_dates", 2290,
                        "UPDATE complaint SET resolved_on = raised_on - 1 WHERE description = 'Bathroom tap is leaking continuously.'"),
                named("ck_complaint_staff", 2290,
                        "UPDATE complaint SET status = 'IN_PROGRESS' WHERE description = 'Washbasin drain is blocked and water does not flow out.'"));
    }

    static Stream<Case> notNullCases() {
        return Stream.of(
                notNull("ck_hostel_user_id_nn", "HOSTEL_USER", "USER_ID", "UPDATE hostel_user SET user_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_hostel_user_full_name_nn", "HOSTEL_USER", "FULL_NAME", "UPDATE hostel_user SET full_name = NULL WHERE ROWNUM = 1"),
                notNull("ck_hostel_user_email_nn", "HOSTEL_USER", "EMAIL", "UPDATE hostel_user SET email = NULL WHERE ROWNUM = 1"),
                notNull("ck_hostel_user_password_nn", "HOSTEL_USER", "PASSWORD", "UPDATE hostel_user SET password = NULL WHERE ROWNUM = 1"),
                notNull("ck_hostel_user_role_nn", "HOSTEL_USER", "USER_ROLE", "UPDATE hostel_user SET user_role = NULL WHERE ROWNUM = 1"),
                notNull("ck_student_reg_no_nn", "STUDENT", "REG_NO", "UPDATE student SET reg_no = NULL WHERE ROWNUM = 1"),
                notNull("ck_student_gender_nn", "STUDENT", "GENDER", "UPDATE student SET gender = NULL WHERE ROWNUM = 1"),
                notNull("ck_student_programme_nn", "STUDENT", "PROGRAMME", "UPDATE student SET programme = NULL WHERE ROWNUM = 1"),
                notNull("ck_student_year_of_study_nn", "STUDENT", "YEAR_OF_STUDY", "UPDATE student SET year_of_study = NULL WHERE ROWNUM = 1"),
                notNull("ck_block_id_nn", "BLOCK", "BLOCK_ID", "UPDATE block SET block_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_block_name_nn", "BLOCK", "BLOCK_NAME", "UPDATE block SET block_name = NULL WHERE ROWNUM = 1"),
                notNull("ck_block_gender_type_nn", "BLOCK", "GENDER_TYPE", "UPDATE block SET gender_type = NULL WHERE ROWNUM = 1"),
                notNull("ck_block_num_floors_nn", "BLOCK", "NUM_FLOORS", "UPDATE block SET num_floors = NULL WHERE ROWNUM = 1"),
                notNull("ck_warden_block_id_nn", "WARDEN", "BLOCK_ID", "UPDATE warden SET block_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_room_floor_no_nn", "ROOM", "FLOOR_NO", "UPDATE room SET floor_no = NULL WHERE ROWNUM = 1"),
                notNull("ck_room_room_type_nn", "ROOM", "ROOM_TYPE", "UPDATE room SET room_type = NULL WHERE ROWNUM = 1"),
                notNull("ck_room_capacity_nn", "ROOM", "CAPACITY", "UPDATE room SET capacity = NULL WHERE ROWNUM = 1"),
                notNull("ck_allocation_alloc_id_nn", "ALLOCATION", "ALLOC_ID", "UPDATE allocation SET alloc_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_allocation_student_id_nn", "ALLOCATION", "STUDENT_ID", "UPDATE allocation SET student_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_allocation_block_id_nn", "ALLOCATION", "BLOCK_ID", "UPDATE allocation SET block_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_allocation_room_no_nn", "ALLOCATION", "ROOM_NO", "UPDATE allocation SET room_no = NULL WHERE ROWNUM = 1"),
                notNull("ck_allocation_approved_by_nn", "ALLOCATION", "APPROVED_BY", "UPDATE allocation SET approved_by = NULL WHERE ROWNUM = 1"),
                notNull("ck_allocation_start_date_nn", "ALLOCATION", "START_DATE", "UPDATE allocation SET start_date = NULL WHERE ROWNUM = 1"),
                notNull("ck_allocation_status_nn", "ALLOCATION", "STATUS", "UPDATE allocation SET status = NULL WHERE ROWNUM = 1"),
                notNull("ck_category_id_nn", "CATEGORY", "CATEGORY_ID", "UPDATE category SET category_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_category_name_nn", "CATEGORY", "CATEGORY_NAME", "UPDATE category SET category_name = NULL WHERE ROWNUM = 1"),
                notNull("ck_category_sla_days_nn", "CATEGORY", "SLA_DAYS", "UPDATE category SET sla_days = NULL WHERE ROWNUM = 1"),
                notNull("ck_staff_id_nn", "STAFF", "STAFF_ID", "UPDATE staff SET staff_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_staff_full_name_nn", "STAFF", "FULL_NAME", "UPDATE staff SET full_name = NULL WHERE ROWNUM = 1"),
                notNull("ck_staff_category_id_nn", "STAFF", "CATEGORY_ID", "UPDATE staff SET category_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_id_nn", "COMPLAINT", "COMPLAINT_ID", "UPDATE complaint SET complaint_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_student_id_nn", "COMPLAINT", "STUDENT_ID", "UPDATE complaint SET student_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_block_id_nn", "COMPLAINT", "BLOCK_ID", "UPDATE complaint SET block_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_room_no_nn", "COMPLAINT", "ROOM_NO", "UPDATE complaint SET room_no = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_category_id_nn", "COMPLAINT", "CATEGORY_ID", "UPDATE complaint SET category_id = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_description_nn", "COMPLAINT", "DESCRIPTION", "UPDATE complaint SET description = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_status_nn", "COMPLAINT", "STATUS", "UPDATE complaint SET status = NULL WHERE ROWNUM = 1"),
                notNull("ck_complaint_raised_on_nn", "COMPLAINT", "RAISED_ON", "UPDATE complaint SET raised_on = NULL WHERE ROWNUM = 1"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("constraintCases")
    void constraintRejectsViolation(Case c) throws SQLException {
        assertViolation(c);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("notNullCases")
    void notNullRejectsNull(Case c) throws SQLException {
        assertViolation(c);
    }

    @Test
    void deletingRoomWithHistoryIsBlocked() throws SQLException {
        assertViolation(named("fk_allocation_room", 2292,
                "DELETE FROM room WHERE room_no = 'A-102' AND block_id = (SELECT block_id FROM block WHERE block_name = 'A Block')"));
    }

    @Test
    void deletingStudentWithHistoryIsBlocked() throws SQLException {
        assertViolation(named("fk_allocation_student", 2292,
                "DELETE FROM hostel_user WHERE email = 'kabir.sethi@hostelhub.test'"));
    }

    @Test
    void deletingCategoryInUseIsBlocked() throws SQLException {
        assertViolation(named("fk_complaint_category", 2292,
                "DELETE FROM category WHERE category_name = 'Electrical'"));
    }

    @Test
    void deletingUserCascadesToStudentWhenNoHistory() throws SQLException {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            try {
                exec(c, "DELETE FROM hostel_user WHERE email = 'naveen.kumar@hostelhub.test'");
                assertThat(queryLong(c, "SELECT COUNT(*) FROM student WHERE reg_no = '25BIT0633'")).isZero();
            } finally {
                c.rollback();
            }
        }
    }

    /** Every constraint in the schema has a case above, so none can be added untested. */
    @Test
    void everyConstraintAndUniqueIndexHasACase() throws SQLException {
        Set<String> covered = new HashSet<>();
        Stream.concat(constraintCases(), notNullCases()).forEach(c -> covered.add(c.constraint().toUpperCase()));
        Set<String> actual = new HashSet<>();
        try (Connection c = connect();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT constraint_name FROM user_constraints WHERE table_name NOT LIKE 'BIN$%' "
                                + "UNION SELECT index_name FROM user_indexes WHERE uniqueness = 'UNIQUE' AND index_type LIKE 'FUNCTION-BASED%'");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                actual.add(rs.getString(1));
            }
        }
        assertThat(covered).containsExactlyInAnyOrderElementsOf(actual);
        assertThat(actual).hasSize(87);
    }

    private static void assertViolation(Case c) throws SQLException {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);
            try {
                for (int i = 0; i < c.sql().length - 1; i++) {
                    exec(conn, c.sql()[i]);
                }
                String violating = c.sql()[c.sql().length - 1];
                assertThatThrownBy(() -> exec(conn, violating))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getErrorCode()).isEqualTo(c.oraCode()))
                        .hasMessageContaining(c.messageFragment());
            } finally {
                conn.rollback();
            }
        }
    }

    static void exec(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
