package com.hostelhub.dao;

import com.hostelhub.model.Staff;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** STAFF. */
public final class StaffDao {

    private static final String SELECT = """
            SELECT st.staff_id, st.full_name, st.phone, st.category_id, c.category_name
            FROM staff st
            JOIN category c ON c.category_id = st.category_id
            """;

    private StaffDao() {
    }

    private static Staff map(ResultSet rs) throws SQLException {
        return new Staff(rs.getLong("staff_id"), rs.getString("full_name"), rs.getString("phone"),
                rs.getLong("category_id"), rs.getString("category_name"));
    }

    public static List<Staff> byCategory(Connection c, long categoryId) throws SQLException {
        return Jdbc.list(c, SELECT + "WHERE st.category_id = ? ORDER BY st.full_name", StaffDao::map, categoryId);
    }

    public static Optional<Staff> find(Connection c, long staffId) throws SQLException {
        return Jdbc.one(c, SELECT + "WHERE st.staff_id = ?", StaffDao::map, staffId);
    }
}
