package com.hostelhub.dao;

import com.hostelhub.model.Block;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/** BLOCK. Blocks are created in setup SQL only (every block needs a warden). */
public final class BlockDao {

    private BlockDao() {
    }

    public static Optional<Block> find(Connection c, long blockId) throws SQLException {
        return Jdbc.one(c, "SELECT block_id, block_name, gender_type, num_floors FROM block WHERE block_id = ?",
                rs -> new Block(rs.getLong("block_id"), rs.getString("block_name"), rs.getString("gender_type"),
                        rs.getInt("num_floors")),
                blockId);
    }

    /** Locks the block row so its details and rooms change one transaction at a time. */
    public static void lock(Connection c, long blockId) throws SQLException {
        Jdbc.count(c, "SELECT block_id FROM block WHERE block_id = ? FOR UPDATE", blockId);
    }

    public static boolean nameTaken(Connection c, String name, long exceptBlockId) throws SQLException {
        return Jdbc.count(c, "SELECT COUNT(*) FROM block WHERE UPPER(block_name) = UPPER(?) AND block_id <> ?",
                name, exceptBlockId) > 0;
    }

    public static int update(Connection c, Block b) throws SQLException {
        return Jdbc.update(c, "UPDATE block SET block_name = ?, gender_type = ?, num_floors = ? WHERE block_id = ?",
                b.blockName(), b.genderType(), b.numFloors(), b.blockId());
    }
}
