package com.hostelhub.dao;

import com.hostelhub.model.Complaint;
import com.hostelhub.model.ComplaintStatus;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** COMPLAINT, with the overdue flag taken from V_OVERDUE_COMPLAINTS (BR-6). */
public final class ComplaintDao {

    private static final String SELECT = """
            SELECT cp.complaint_id, cp.student_id, u.full_name AS student_name, s.reg_no, cp.block_id, b.block_name,
                   cp.room_no, cp.category_id, cat.category_name, cat.sla_days, cp.staff_id, st.full_name AS staff_name,
                   cp.description, cp.status, cp.raised_on, cp.resolved_on,
                   CASE WHEN o.complaint_id IS NULL THEN 0 ELSE 1 END AS overdue, NVL(o.days_overdue, 0) AS days_overdue
            FROM complaint cp
            JOIN student s ON s.user_id = cp.student_id
            JOIN hostel_user u ON u.user_id = cp.student_id
            JOIN block b ON b.block_id = cp.block_id
            JOIN category cat ON cat.category_id = cp.category_id
            LEFT JOIN staff st ON st.staff_id = cp.staff_id
            LEFT JOIN v_overdue_complaints o ON o.complaint_id = cp.complaint_id
            """;

    private ComplaintDao() {
    }

    private static Complaint map(ResultSet rs) throws SQLException {
        return new Complaint(rs.getLong("complaint_id"), rs.getLong("student_id"), rs.getString("student_name"),
                rs.getString("reg_no"), rs.getLong("block_id"), rs.getString("block_name"), rs.getString("room_no"),
                rs.getLong("category_id"), rs.getString("category_name"), rs.getInt("sla_days"),
                Jdbc.nullableLong(rs, "staff_id"), rs.getString("staff_name"), rs.getString("description"),
                ComplaintStatus.valueOf(rs.getString("status")), Jdbc.dateTime(rs, "raised_on"),
                Jdbc.dateTime(rs, "resolved_on"), rs.getInt("overdue") == 1, rs.getInt("days_overdue"));
    }

    public static long insert(Connection c, long studentId, long blockId, String roomNo, long categoryId,
            String description) throws SQLException {
        return Jdbc.insert(c, """
                INSERT INTO complaint (student_id, block_id, room_no, category_id, description, status, raised_on)
                VALUES (?, ?, ?, ?, ?, 'OPEN', SYSDATE)""", "complaint_id",
                studentId, blockId, roomNo, categoryId, description);
    }

    public static List<Complaint> forStudent(Connection c, long studentId) throws SQLException {
        return Jdbc.list(c, SELECT + "WHERE cp.student_id = ? ORDER BY cp.raised_on DESC", ComplaintDao::map, studentId);
    }

    /** A block's complaints; status null means any, overdueOnly limits to V_OVERDUE_COMPLAINTS. */
    public static List<Complaint> forBlock(Connection c, long blockId, ComplaintStatus status, boolean overdueOnly)
            throws SQLException {
        String s = status == null ? null : status.name();
        return Jdbc.list(c, SELECT + """
                WHERE cp.block_id = ? AND (? IS NULL OR cp.status = ?) AND (? = 0 OR o.complaint_id IS NOT NULL)
                ORDER BY cp.raised_on DESC""", ComplaintDao::map, blockId, s, s, overdueOnly ? 1 : 0);
    }

    /** FR-11: unresolved complaints of a block, oldest first. */
    public static List<Complaint> openForBlock(Connection c, long blockId) throws SQLException {
        return Jdbc.list(c, SELECT + "WHERE cp.block_id = ? AND cp.status <> 'RESOLVED' ORDER BY cp.raised_on",
                ComplaintDao::map, blockId);
    }

    /** FR-11: rows of V_OVERDUE_COMPLAINTS for a block, most overdue first. */
    public static List<Complaint> overdueForBlock(Connection c, long blockId) throws SQLException {
        return Jdbc.list(c, SELECT + "WHERE cp.block_id = ? AND o.complaint_id IS NOT NULL ORDER BY o.days_overdue DESC",
                ComplaintDao::map, blockId);
    }

    /** Locks and returns the complaint row. */
    public static Optional<Complaint> lock(Connection c, long complaintId) throws SQLException {
        Jdbc.count(c, "SELECT complaint_id FROM complaint WHERE complaint_id = ? FOR UPDATE", complaintId);
        return Jdbc.one(c, SELECT + "WHERE cp.complaint_id = ?", ComplaintDao::map, complaintId);
    }

    public static int assignStaff(Connection c, long complaintId, long staffId) throws SQLException {
        return Jdbc.update(c, "UPDATE complaint SET staff_id = ? WHERE complaint_id = ?", staffId, complaintId);
    }

    /** Sets the status; RESOLVED also sets resolved_on = SYSDATE. */
    public static int updateStatus(Connection c, long complaintId, ComplaintStatus status) throws SQLException {
        return Jdbc.update(c, """
                UPDATE complaint
                SET status = ?, resolved_on = CASE WHEN ? = 'RESOLVED' THEN SYSDATE END
                WHERE complaint_id = ?""", status.name(), status.name(), complaintId);
    }

    public static int countOverdue(Connection c, long blockId) throws SQLException {
        return (int) Jdbc.count(c, "SELECT COUNT(*) FROM v_overdue_complaints WHERE block_id = ?", blockId);
    }

    public static int countOpen(Connection c, long blockId) throws SQLException {
        return (int) Jdbc.count(c, "SELECT COUNT(*) FROM complaint WHERE block_id = ? AND status <> 'RESOLVED'", blockId);
    }
}
