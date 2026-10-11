package com.hostelhub.dao;

import com.hostelhub.model.Allocation;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** ALLOCATION. */
public final class AllocationDao {

    private static final String SELECT = """
            SELECT a.alloc_id, a.student_id, u.full_name, s.reg_no, a.block_id, a.room_no,
                   w.full_name AS approved_by_name, a.start_date, a.end_date, a.status
            FROM allocation a
            JOIN student s ON s.user_id = a.student_id
            JOIN hostel_user u ON u.user_id = a.student_id
            JOIN hostel_user w ON w.user_id = a.approved_by
            """;

    private AllocationDao() {
    }

    private static Allocation map(ResultSet rs) throws SQLException {
        return new Allocation(rs.getLong("alloc_id"), rs.getLong("student_id"), rs.getString("full_name"),
                rs.getString("reg_no"), rs.getLong("block_id"), rs.getString("room_no"),
                rs.getString("approved_by_name"), Jdbc.date(rs, "start_date"), Jdbc.date(rs, "end_date"),
                rs.getString("status"));
    }

    public static Optional<Allocation> activeFor(Connection c, long studentId) throws SQLException {
        return Jdbc.one(c, SELECT + "WHERE a.student_id = ? AND a.status = 'ACTIVE'", AllocationDao::map, studentId);
    }

    /** Locks and returns the allocation row. */
    public static Optional<Allocation> lock(Connection c, long allocId) throws SQLException {
        Jdbc.count(c, "SELECT alloc_id FROM allocation WHERE alloc_id = ? FOR UPDATE", allocId);
        return Jdbc.one(c, SELECT + "WHERE a.alloc_id = ?", AllocationDao::map, allocId);
    }

    public static int countActive(Connection c, long blockId, String roomNo) throws SQLException {
        return (int) Jdbc.count(c,
                "SELECT COUNT(*) FROM allocation WHERE block_id = ? AND room_no = ? AND status = 'ACTIVE'",
                blockId, roomNo);
    }

    public static int countActiveInBlock(Connection c, long blockId) throws SQLException {
        return (int) Jdbc.count(c, "SELECT COUNT(*) FROM allocation WHERE block_id = ? AND status = 'ACTIVE'", blockId);
    }

    public static long insertActive(Connection c, long studentId, long blockId, String roomNo, long approvedBy)
            throws SQLException {
        return Jdbc.insert(c, """
                INSERT INTO allocation (student_id, block_id, room_no, approved_by, start_date, status)
                VALUES (?, ?, ?, ?, SYSDATE, 'ACTIVE')""", "alloc_id", studentId, blockId, roomNo, approvedBy);
    }

    public static int vacate(Connection c, long allocId) throws SQLException {
        return Jdbc.update(c, """
                UPDATE allocation SET status = 'VACATED', end_date = SYSDATE
                WHERE alloc_id = ? AND status = 'ACTIVE'""", allocId);
    }

    /** Allocation history for a block; status null means all. Newest first. */
    public static List<Allocation> history(Connection c, long blockId, String status) throws SQLException {
        return Jdbc.list(c, SELECT + """
                WHERE a.block_id = ? AND (? IS NULL OR a.status = ?)
                ORDER BY a.start_date DESC, a.alloc_id DESC""", AllocationDao::map, blockId, status, status);
    }
}
