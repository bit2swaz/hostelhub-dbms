package com.hostelhub.dao;

import com.hostelhub.model.Role;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** HOSTEL_USER. */
public final class UserDao {

    public record Credentials(long userId, String fullName, String email, String passwordHash, Role role) {
    }

    private UserDao() {
    }

    public static Optional<Credentials> findByEmail(Connection c, String email) throws SQLException {
        return Jdbc.one(c, """
                SELECT user_id, full_name, email, password, user_role
                FROM hostel_user
                WHERE LOWER(email) = LOWER(?)""",
                rs -> new Credentials(rs.getLong("user_id"), rs.getString("full_name"), rs.getString("email"),
                        rs.getString("password"), Role.valueOf(rs.getString("user_role"))),
                email);
    }

    public static boolean emailTaken(Connection c, String email, long exceptUserId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM hostel_user WHERE LOWER(email) = LOWER(?) AND user_id <> ?",
                email, exceptUserId) > 0;
    }

    public static long insert(Connection c, String fullName, String email, String phone, String passwordHash, Role role)
            throws SQLException {
        return Jdbc.insert(c, """
                INSERT INTO hostel_user (full_name, email, phone, password, user_role)
                VALUES (?, ?, ?, ?, ?)""", "user_id", fullName, email, phone, passwordHash, role.name());
    }

    public static int update(Connection c, long userId, String fullName, String email, String phone) throws SQLException {
        return Jdbc.update(c, "UPDATE hostel_user SET full_name = ?, email = ?, phone = ? WHERE user_id = ?",
                fullName, email, phone, userId);
    }

    /** Deletes the user; STUDENT/WARDEN rows go with it (ON DELETE CASCADE). */
    public static int delete(Connection c, long userId) throws SQLException {
        return Jdbc.update(c, "DELETE FROM hostel_user WHERE user_id = ?", userId);
    }
}
