package com.hostelhub.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.support.OracleTestBase;
import com.hostelhub.util.PasswordHasher;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The created schema matches PRD section 5 exactly, and the seed matches section 9. */
class SchemaTest extends OracleTestBase {

    private static List<String> rows(String sql) throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            int cols = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= cols; i++) {
                    sb.append(i > 1 ? " " : "").append(rs.getString(i));
                }
                out.add(sb.toString());
            }
        }
        return out;
    }

    @Test
    void tablesAndColumnsMatchPrd() throws SQLException {
        // TABLE COLUMN TYPE LENGTH/PRECISION NULLABLE, in column order.
        assertThat(rows("SELECT table_name, column_name, data_type, "
                + "CASE WHEN data_type = 'NUMBER' THEN NVL(TO_CHAR(data_precision), '-') "
                + "WHEN data_type = 'DATE' THEN '-' ELSE TO_CHAR(char_length) END, nullable "
                + "FROM user_tab_columns WHERE table_name IN ('HOSTEL_USER','STUDENT','BLOCK','WARDEN','ROOM',"
                + "'ALLOCATION','CATEGORY','STAFF','COMPLAINT') ORDER BY table_name, column_id"))
                .containsExactly(
                        "ALLOCATION ALLOC_ID NUMBER - N",
                        "ALLOCATION STUDENT_ID NUMBER - N",
                        "ALLOCATION BLOCK_ID NUMBER - N",
                        "ALLOCATION ROOM_NO VARCHAR2 6 N",
                        "ALLOCATION APPROVED_BY NUMBER - N",
                        "ALLOCATION START_DATE DATE - N",
                        "ALLOCATION END_DATE DATE - Y",
                        "ALLOCATION STATUS VARCHAR2 8 N",
                        "BLOCK BLOCK_ID NUMBER - N",
                        "BLOCK BLOCK_NAME VARCHAR2 30 N",
                        "BLOCK GENDER_TYPE CHAR 1 N",
                        "BLOCK NUM_FLOORS NUMBER 2 N",
                        "CATEGORY CATEGORY_ID NUMBER - N",
                        "CATEGORY CATEGORY_NAME VARCHAR2 30 N",
                        "CATEGORY SLA_DAYS NUMBER 3 N",
                        "COMPLAINT COMPLAINT_ID NUMBER - N",
                        "COMPLAINT STUDENT_ID NUMBER - N",
                        "COMPLAINT BLOCK_ID NUMBER - N",
                        "COMPLAINT ROOM_NO VARCHAR2 6 N",
                        "COMPLAINT CATEGORY_ID NUMBER - N",
                        "COMPLAINT STAFF_ID NUMBER - Y",
                        "COMPLAINT DESCRIPTION VARCHAR2 500 N",
                        "COMPLAINT STATUS VARCHAR2 12 N",
                        "COMPLAINT RAISED_ON DATE - N",
                        "COMPLAINT RESOLVED_ON DATE - Y",
                        "HOSTEL_USER USER_ID NUMBER - N",
                        "HOSTEL_USER FULL_NAME VARCHAR2 100 N",
                        "HOSTEL_USER EMAIL VARCHAR2 120 N",
                        "HOSTEL_USER PHONE VARCHAR2 10 Y",
                        "HOSTEL_USER PASSWORD VARCHAR2 200 N",
                        "HOSTEL_USER USER_ROLE VARCHAR2 10 N",
                        "ROOM BLOCK_ID NUMBER - N",
                        "ROOM ROOM_NO VARCHAR2 6 N",
                        "ROOM FLOOR_NO NUMBER 2 N",
                        "ROOM ROOM_TYPE VARCHAR2 10 N",
                        "ROOM CAPACITY NUMBER 1 N",
                        "STAFF STAFF_ID NUMBER - N",
                        "STAFF FULL_NAME VARCHAR2 100 N",
                        "STAFF PHONE VARCHAR2 10 Y",
                        "STAFF CATEGORY_ID NUMBER - N",
                        "STUDENT USER_ID NUMBER - N",
                        "STUDENT REG_NO VARCHAR2 10 N",
                        "STUDENT GENDER CHAR 1 N",
                        "STUDENT PROGRAMME VARCHAR2 60 N",
                        "STUDENT YEAR_OF_STUDY NUMBER 1 N",
                        "WARDEN USER_ID NUMBER - N",
                        "WARDEN BLOCK_ID NUMBER - N",
                        "WARDEN OFFICE_NO VARCHAR2 10 Y");
    }

    @Test
    void surrogateKeysAreIdentityByDefault() throws SQLException {
        assertThat(rows("SELECT table_name, column_name, generation_type FROM user_tab_identity_cols ORDER BY 1"))
                .containsExactly(
                        "ALLOCATION ALLOC_ID BY DEFAULT",
                        "BLOCK BLOCK_ID BY DEFAULT",
                        "CATEGORY CATEGORY_ID BY DEFAULT",
                        "COMPLAINT COMPLAINT_ID BY DEFAULT",
                        "HOSTEL_USER USER_ID BY DEFAULT",
                        "STAFF STAFF_ID BY DEFAULT");
    }

    @Test
    void keysAndForeignKeysMatchPrd() throws SQLException {
        // constraint, referenced constraint, delete rule
        assertThat(rows("SELECT c.constraint_name, r.constraint_name, c.delete_rule FROM user_constraints c "
                + "JOIN user_constraints r ON r.constraint_name = c.r_constraint_name "
                + "WHERE c.constraint_type = 'R' ORDER BY 1"))
                .containsExactly(
                        "FK_ALLOCATION_ROOM PK_ROOM NO ACTION",
                        "FK_ALLOCATION_STUDENT PK_STUDENT NO ACTION",
                        "FK_ALLOCATION_WARDEN PK_WARDEN NO ACTION",
                        "FK_COMPLAINT_CATEGORY PK_CATEGORY NO ACTION",
                        "FK_COMPLAINT_ROOM PK_ROOM NO ACTION",
                        "FK_COMPLAINT_STAFF PK_STAFF NO ACTION",
                        "FK_COMPLAINT_STUDENT PK_STUDENT NO ACTION",
                        "FK_ROOM_BLOCK PK_BLOCK NO ACTION",
                        "FK_STAFF_CATEGORY PK_CATEGORY NO ACTION",
                        "FK_STUDENT_USER PK_HOSTEL_USER CASCADE",
                        "FK_WARDEN_BLOCK PK_BLOCK NO ACTION",
                        "FK_WARDEN_USER PK_HOSTEL_USER CASCADE");
        assertThat(rows("SELECT c.constraint_name, LISTAGG(cc.column_name, ',') WITHIN GROUP (ORDER BY cc.position) "
                + "FROM user_constraints c JOIN user_cons_columns cc ON cc.constraint_name = c.constraint_name "
                + "WHERE c.constraint_type IN ('P', 'U') GROUP BY c.constraint_name ORDER BY 1"))
                .containsExactly(
                        "PK_ALLOCATION ALLOC_ID",
                        "PK_BLOCK BLOCK_ID",
                        "PK_CATEGORY CATEGORY_ID",
                        "PK_COMPLAINT COMPLAINT_ID",
                        "PK_HOSTEL_USER USER_ID",
                        "PK_ROOM BLOCK_ID,ROOM_NO",
                        "PK_STAFF STAFF_ID",
                        "PK_STUDENT USER_ID",
                        "PK_WARDEN USER_ID",
                        "UQ_BLOCK_NAME BLOCK_NAME",
                        "UQ_CATEGORY_NAME CATEGORY_NAME",
                        "UQ_HOSTEL_USER_EMAIL EMAIL",
                        "UQ_STUDENT_REG_NO REG_NO",
                        "UQ_WARDEN_BLOCK BLOCK_ID");
    }

    @Test
    void everyConstraintIsNamedWithPrdPrefix() throws SQLException {
        assertThat(rows("SELECT constraint_name FROM user_constraints "
                + "WHERE NOT REGEXP_LIKE(constraint_name, '^(PK|FK|UQ|CK)_')")).isEmpty();
        assertThat(rows("SELECT index_name, uniqueness, index_type FROM user_indexes WHERE index_name = 'UX_ALLOC_ONE_ACTIVE'"))
                .containsExactly("UX_ALLOC_ONE_ACTIVE UNIQUE FUNCTION-BASED NORMAL");
    }

    @Test
    void viewsAndTriggersAreValidWithPrdTiming() throws SQLException {
        assertThat(rows("SELECT object_name, status FROM user_objects WHERE object_type IN ('VIEW', 'TRIGGER') ORDER BY 1"))
                .containsExactly(
                        "TRG_ALLOC_CAPACITY VALID",
                        "TRG_ALLOC_GENDER VALID",
                        "TRG_ALLOC_WARDEN_BLOCK VALID",
                        "TRG_COMPLAINT_STAFF_CAT VALID",
                        "V_OVERDUE_COMPLAINTS VALID",
                        "V_ROOM_OCCUPANCY VALID");
        assertThat(rows("SELECT trigger_name, trigger_type, triggering_event, table_name, status FROM user_triggers ORDER BY 1"))
                .containsExactly(
                        "TRG_ALLOC_CAPACITY AFTER STATEMENT INSERT OR UPDATE ALLOCATION ENABLED",
                        "TRG_ALLOC_GENDER BEFORE EACH ROW INSERT ALLOCATION ENABLED",
                        "TRG_ALLOC_WARDEN_BLOCK BEFORE EACH ROW INSERT ALLOCATION ENABLED",
                        "TRG_COMPLAINT_STAFF_CAT BEFORE EACH ROW INSERT OR UPDATE COMPLAINT ENABLED");
        assertThat(rows("SELECT column_name FROM user_trigger_cols WHERE trigger_name = 'TRG_COMPLAINT_STAFF_CAT' "
                + "AND column_usage LIKE '%NEW%' AND column_list = 'YES'")).containsExactly("STAFF_ID");
        assertThat(rows("SELECT column_name FROM user_tab_columns WHERE table_name = 'V_ROOM_OCCUPANCY' ORDER BY column_id"))
                .containsExactly("BLOCK_ID", "BLOCK_NAME", "ROOM_NO", "FLOOR_NO", "ROOM_TYPE", "CAPACITY", "OCCUPANCY", "VACANCIES");
    }

    @Test
    void seedMatchesPrdSection9() throws SQLException {
        assertThat(rows("SELECT block_name, gender_type, num_floors FROM block ORDER BY 1"))
                .containsExactly("A Block M 4", "B Block F 4", "C Block M 3");
        assertThat(rows("SELECT u.email, b.block_name FROM warden w JOIN hostel_user u ON u.user_id = w.user_id "
                + "JOIN block b ON b.block_id = w.block_id ORDER BY 1"))
                .containsExactly("warden.a@hostelhub.test A Block", "warden.b@hostelhub.test B Block", "warden.c@hostelhub.test C Block");
        assertThat(rows("SELECT category_name, sla_days FROM category ORDER BY category_id"))
                .containsExactly("Electrical 2", "Plumbing 2", "Carpentry 5", "Internet 1", "Housekeeping 1");
        assertThat(rows("SELECT COUNT(*), COUNT(DISTINCT category_id) FROM staff")).containsExactly("8 5");
        assertThat(rows("SELECT b.block_name, COUNT(*), COUNT(DISTINCT r.room_type) FROM room r "
                + "JOIN block b ON b.block_id = r.block_id GROUP BY b.block_name ORDER BY 1"))
                .containsExactly("A Block 12 4", "B Block 12 4", "C Block 12 4");
        assertThat(rows("SELECT gender, COUNT(*) FROM student GROUP BY gender ORDER BY 1")).containsExactly("F 15", "M 30");
        assertThat(queryLong("SELECT COUNT(*) FROM allocation WHERE status = 'ACTIVE'")).isEqualTo(38);
        assertThat(queryLong("SELECT COUNT(*) FROM student s WHERE NOT EXISTS "
                + "(SELECT 1 FROM allocation a WHERE a.student_id = s.user_id AND a.status = 'ACTIVE')")).isEqualTo(7);
        assertThat(queryLong("SELECT COUNT(DISTINCT student_id) FROM allocation WHERE status = 'VACATED'")).isGreaterThanOrEqualTo(3);
        assertThat(rows("SELECT status, COUNT(*) FROM complaint GROUP BY status ORDER BY 1"))
                .containsExactly("IN_PROGRESS 4", "OPEN 6", "RESOLVED 5");
        assertThat(queryLong("SELECT COUNT(*) FROM v_overdue_complaints")).isGreaterThanOrEqualTo(3);
        assertThat(queryLong("SELECT COUNT(*) FROM complaint WHERE status = 'OPEN' AND staff_id IS NULL")).isGreaterThanOrEqualTo(1);
        // Complaints are raised for the student's own active room.
        assertThat(queryLong("SELECT COUNT(*) FROM complaint cp WHERE NOT EXISTS (SELECT 1 FROM allocation a "
                + "WHERE a.student_id = cp.student_id AND a.block_id = cp.block_id AND a.room_no = cp.room_no AND a.status = 'ACTIVE')"))
                .isZero();
    }

    @Test
    void seedPasswordsAreOnlyPbkdf2HashesOfTheDemoPassword() throws SQLException {
        List<String> hashes = rows("SELECT password FROM hostel_user");
        assertThat(hashes).hasSize(48).doesNotHaveDuplicates()
                .allMatch(h -> h.matches("pbkdf2\\$600000\\$[A-Za-z0-9+/=]{24}\\$[A-Za-z0-9+/=]{44}"));
        assertThat(hashes).allMatch(h -> PasswordHasher.verify(SEED_PASSWORD, h));
    }
}
