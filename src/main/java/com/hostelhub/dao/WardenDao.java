package com.hostelhub.dao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** WARDEN. */
public final class WardenDao {

    private WardenDao() {
    }

    /** The block this warden manages (1:1, BR-4). */
    public static Optional<Long> blockIdFor(Connection c, long userId) throws SQLException {
        return Jdbc.one(c, "SELECT block_id FROM warden WHERE user_id = ?", rs -> rs.getLong(1), userId);
    }
}
