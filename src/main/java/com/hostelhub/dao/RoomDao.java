package com.hostelhub.dao;

import com.hostelhub.model.Room;
import com.hostelhub.model.RoomType;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** ROOM, read through V_ROOM_OCCUPANCY for the derived occupancy. */
public final class RoomDao {

    private static final String SELECT = """
            SELECT block_id, block_name, room_no, floor_no, room_type, capacity, occupancy
            FROM v_room_occupancy
            """;

    private RoomDao() {
    }

    private static Room map(ResultSet rs) throws SQLException {
        return new Room(rs.getLong("block_id"), rs.getString("block_name"), rs.getString("room_no"),
                rs.getInt("floor_no"), RoomType.valueOf(rs.getString("room_type")), rs.getInt("capacity"),
                rs.getInt("occupancy"));
    }

    public static List<Room> listByBlock(Connection c, long blockId) throws SQLException {
        return Jdbc.list(c, SELECT + "WHERE block_id = ? ORDER BY floor_no, room_no", RoomDao::map, blockId);
    }

    /** FR-04 picker: rooms in the block with at least one free bed. */
    public static List<Room> withVacancies(Connection c, long blockId) throws SQLException {
        return Jdbc.list(c, SELECT + "WHERE block_id = ? AND vacancies > 0 ORDER BY floor_no, room_no",
                RoomDao::map, blockId);
    }

    public static Optional<Room> find(Connection c, long blockId, String roomNo) throws SQLException {
        return Jdbc.one(c, SELECT + "WHERE block_id = ? AND room_no = ?", RoomDao::map, blockId, roomNo);
    }

    /**
     * Locks the ROOM row (SELECT ... FOR UPDATE) so concurrent allocations to the same room
     * are serialised (BR-1). Returns false if the room does not exist.
     */
    public static boolean lock(Connection c, long blockId, String roomNo) throws SQLException {
        return Jdbc.one(c, "SELECT room_no FROM room WHERE block_id = ? AND room_no = ? FOR UPDATE",
                rs -> rs.getString(1), blockId, roomNo).isPresent();
    }

    public static int maxFloor(Connection c, long blockId) throws SQLException {
        return (int) Jdbc.count(c, "SELECT NVL(MAX(floor_no), -1) FROM room WHERE block_id = ?", blockId);
    }

    public static void insert(Connection c, long blockId, String roomNo, int floorNo, RoomType type) throws SQLException {
        Jdbc.update(c, """
                INSERT INTO room (block_id, room_no, floor_no, room_type, capacity)
                VALUES (?, ?, ?, ?, ?)""", blockId, roomNo, floorNo, type.name(), type.capacity());
    }

    public static int update(Connection c, long blockId, String roomNo, int floorNo, RoomType type) throws SQLException {
        return Jdbc.update(c, """
                UPDATE room SET floor_no = ?, room_type = ?, capacity = ?
                WHERE block_id = ? AND room_no = ?""", floorNo, type.name(), type.capacity(), blockId, roomNo);
    }

    public static int delete(Connection c, long blockId, String roomNo) throws SQLException {
        return Jdbc.update(c, "DELETE FROM room WHERE block_id = ? AND room_no = ?", blockId, roomNo);
    }

    /** True if any allocation (active or vacated) or complaint refers to the room. */
    public static boolean hasHistory(Connection c, long blockId, String roomNo) throws SQLException {
        return Jdbc.count(c, """
                SELECT (SELECT COUNT(*) FROM allocation WHERE block_id = ? AND room_no = ?)
                     + (SELECT COUNT(*) FROM complaint WHERE block_id = ? AND room_no = ?)
                FROM dual""", blockId, roomNo, blockId, roomNo) > 0;
    }
}
