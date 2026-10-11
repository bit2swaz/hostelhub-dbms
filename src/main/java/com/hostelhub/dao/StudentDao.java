package com.hostelhub.dao;

import com.hostelhub.model.Student;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** STUDENT (joined with HOSTEL_USER and the current ACTIVE allocation). */
public final class StudentDao {

    private static final String SELECT = """
            SELECT u.user_id, u.full_name, u.email, u.phone, s.reg_no, s.gender, s.programme, s.year_of_study,
                   b.block_name, a.room_no
            FROM student s
            JOIN hostel_user u ON u.user_id = s.user_id
            LEFT JOIN allocation a ON a.student_id = s.user_id AND a.status = 'ACTIVE'
            LEFT JOIN block b ON b.block_id = a.block_id
            """;

    private StudentDao() {
    }

    private static Student map(ResultSet rs) throws SQLException {
        return new Student(rs.getLong("user_id"), rs.getString("full_name"), rs.getString("email"),
                rs.getString("phone"), rs.getString("reg_no"), rs.getString("gender"), rs.getString("programme"),
                rs.getInt("year_of_study"), rs.getString("block_name"), rs.getString("room_no"));
    }

    /** All students whose reg_no or name contains the text (case-insensitive); empty text returns all. */
    public static List<Student> search(Connection c, String text) throws SQLException {
        String like = "%" + (text == null ? "" : text.trim().toUpperCase()) + "%";
        return Jdbc.list(c, SELECT + """
                WHERE UPPER(s.reg_no) LIKE ? OR UPPER(u.full_name) LIKE ?
                ORDER BY s.reg_no""", StudentDao::map, like, like);
    }

    public static Optional<Student> find(Connection c, long userId) throws SQLException {
        return Jdbc.one(c, SELECT + "WHERE s.user_id = ?", StudentDao::map, userId);
    }

    /** BR-2/BR-3 picker: students of the given gender with no ACTIVE allocation. */
    public static List<Student> unallocated(Connection c, String gender) throws SQLException {
        return Jdbc.list(c, SELECT + """
                WHERE s.gender = ? AND a.alloc_id IS NULL
                ORDER BY s.reg_no""", StudentDao::map, gender);
    }

    /** Students with an ACTIVE allocation in the room (FR-06 roommates). */
    public static List<Student> inRoom(Connection c, long blockId, String roomNo) throws SQLException {
        return Jdbc.list(c, SELECT + """
                WHERE a.block_id = ? AND a.room_no = ?
                ORDER BY u.full_name""", StudentDao::map, blockId, roomNo);
    }

    public static boolean regNoTaken(Connection c, String regNo, long exceptUserId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM student WHERE reg_no = ? AND user_id <> ?", regNo, exceptUserId) > 0;
    }

    public static void insert(Connection c, long userId, Student s) throws SQLException {
        Jdbc.update(c, """
                INSERT INTO student (user_id, reg_no, gender, programme, year_of_study)
                VALUES (?, ?, ?, ?, ?)""", userId, s.regNo(), s.gender(), s.programme(), s.yearOfStudy());
    }

    public static int update(Connection c, Student s) throws SQLException {
        return Jdbc.update(c, """
                UPDATE student SET reg_no = ?, gender = ?, programme = ?, year_of_study = ?
                WHERE user_id = ?""", s.regNo(), s.gender(), s.programme(), s.yearOfStudy(), s.userId());
    }

    /** True if the student has any allocation (active or vacated) or any complaint. */
    public static boolean hasHistory(Connection c, long userId) throws SQLException {
        return Jdbc.count(c, """
                SELECT (SELECT COUNT(*) FROM allocation WHERE student_id = ?)
                     + (SELECT COUNT(*) FROM complaint WHERE student_id = ?)
                FROM dual""", userId, userId) > 0;
    }
}
