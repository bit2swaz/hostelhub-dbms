package com.hostelhub.service;

import com.hostelhub.dao.AllocationDao;
import com.hostelhub.dao.BlockDao;
import com.hostelhub.dao.RoomDao;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.model.Block;
import com.hostelhub.model.Room;
import com.hostelhub.model.RoomType;
import com.hostelhub.util.Session;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** FR-02: a warden edits their own block and manages its rooms (BR-4 scoping by session block). */
public class RoomService {

    private final ConnectionFactory db;

    public RoomService(ConnectionFactory db) {
        this.db = db;
    }

    public Block block(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> BlockDao.find(c, blockId).orElseThrow());
    }

    public Block updateBlock(Session s, String name, String genderType, int numFloors) {
        long blockId = Validators.wardenBlock(s);
        Validators.block(name, genderType, numFloors);
        String n = name.trim();
        return Tx.run(db, c -> {
            BlockDao.lock(c, blockId);
            Block current = BlockDao.find(c, blockId).orElseThrow();
            if (BlockDao.nameTaken(c, n, blockId)) {
                throw new ServiceException("That block name is already in use.");
            }
            int maxFloor = RoomDao.maxFloor(c, blockId);
            if (numFloors <= maxFloor) {
                throw new ServiceException(current.blockName() + " has rooms on floor " + maxFloor
                        + ", so it needs at least " + (maxFloor + 1) + " floors.");
            }
            if (!current.genderType().equals(genderType) && AllocationDao.countActiveInBlock(c, blockId) > 0) {
                throw new ServiceException("Cannot change the block's gender while it has active allocations (BR-3).");
            }
            Block updated = new Block(blockId, n, genderType, numFloors);
            BlockDao.update(c, updated);
            return updated;
        });
    }

    public List<Room> rooms(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> RoomDao.listByBlock(c, blockId));
    }

    public void addRoom(Session s, String roomNo, int floorNo, RoomType type) {
        long blockId = Validators.wardenBlock(s);
        Tx.run(db, c -> {
            BlockDao.lock(c, blockId);
            Block b = BlockDao.find(c, blockId).orElseThrow();
            Validators.room(roomNo, floorNo, b.numFloors());
            String no = roomNo.trim().toUpperCase();
            if (RoomDao.find(c, blockId, no).isPresent()) {
                throw new ServiceException("Room " + no + " already exists in " + b.blockName() + ".");
            }
            RoomDao.insert(c, blockId, no, floorNo, type);
            return null;
        });
    }

    /** Changes floor and type. Capacity follows the type and may not drop below current occupancy (BR-1). */
    public void updateRoom(Session s, String roomNo, int floorNo, RoomType type) {
        long blockId = Validators.wardenBlock(s);
        Tx.run(db, c -> {
            Room r = lockRoom(c, blockId, roomNo);
            Block b = BlockDao.find(c, blockId).orElseThrow();
            Validators.room(roomNo, floorNo, b.numFloors());
            if (type.capacity() < r.occupancy()) {
                throw new ServiceException("Room " + r.roomNo() + " has " + r.occupancy() + " occupants; a " + type
                        + " room holds only " + type.capacity() + " (BR-1).");
            }
            RoomDao.update(c, blockId, r.roomNo(), floorNo, type);
            return null;
        });
    }

    public void deleteRoom(Session s, String roomNo) {
        long blockId = Validators.wardenBlock(s);
        Tx.run(db, c -> {
            Room r = lockRoom(c, blockId, roomNo);
            if (RoomDao.hasHistory(c, blockId, r.roomNo())) {
                throw new ServiceException("Room " + r.roomNo()
                        + " has allocation or complaint history and cannot be deleted.");
            }
            return RoomDao.delete(c, blockId, r.roomNo());
        });
    }

    /** Locks a room of the warden's own block; rooms of other blocks are simply not found (BR-4). */
    static Room lockRoom(Connection c, long blockId, String roomNo) throws SQLException {
        if (roomNo == null || !RoomDao.lock(c, blockId, roomNo)) {
            throw new ServiceException("Room " + roomNo + " was not found in your block.");
        }
        return RoomDao.find(c, blockId, roomNo).orElseThrow();
    }
}
