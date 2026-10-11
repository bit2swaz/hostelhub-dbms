package com.hostelhub.service;

import com.hostelhub.dao.AllocationDao;
import com.hostelhub.dao.BlockDao;
import com.hostelhub.dao.RoomDao;
import com.hostelhub.dao.StudentDao;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.model.Allocation;
import com.hostelhub.model.Block;
import com.hostelhub.model.Room;
import com.hostelhub.model.Student;
import com.hostelhub.util.Session;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** FR-04 allocate and FR-05 vacate/transfer, enforcing BR-1 to BR-4 before the database does. */
public class AllocationService {

    private final ConnectionFactory db;

    public AllocationService(ConnectionFactory db) {
        this.db = db;
    }

    /** BR-2/BR-3 picker: unallocated students whose gender matches the warden's block. */
    public List<Student> eligibleStudents(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> StudentDao.unallocated(c, BlockDao.find(c, blockId).orElseThrow().genderType()));
    }

    /** BR-1 picker: rooms of the warden's block with a free bed. */
    public List<Room> availableRooms(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> RoomDao.withVacancies(c, blockId));
    }

    public List<Allocation> history(Session s, String status) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> AllocationDao.history(c, blockId, status));
    }

    /** Allocates a bed. The ROOM row is locked FOR UPDATE, so two wardens cannot both take the last bed. */
    public long allocate(Session s, long studentId, String roomNo) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> {
            Room room = RoomService.lockRoom(c, blockId, roomNo);
            Student st = StudentDao.find(c, studentId).orElseThrow(() -> new ServiceException("Student not found."));
            Block block = BlockDao.find(c, blockId).orElseThrow();
            if (!st.gender().equals(block.genderType())) {
                throw new ServiceException("Student gender does not match the block's gender (BR-3).");
            }
            if (AllocationDao.activeFor(c, studentId).isPresent()) {
                throw new ServiceException("Student " + st.regNo() + " already has an active allocation (BR-2).");
            }
            requireFreeBed(c, room);
            return AllocationDao.insertActive(c, studentId, blockId, room.roomNo(), s.userId());
        });
    }

    /** Vacates: status VACATED, end_date SYSDATE. The row is kept as history. */
    public void vacate(Session s, long allocId) {
        long blockId = Validators.wardenBlock(s);
        Tx.run(db, c -> {
            Allocation a = lockActive(c, allocId, blockId);
            return AllocationDao.vacate(c, a.allocId());
        });
    }

    /**
     * Transfers within the warden's block in ONE transaction: vacate the current allocation,
     * then lock the target room, check BR-1 and insert the new ACTIVE allocation. If any step
     * fails, the rollback restores the original allocation.
     */
    public long transfer(Session s, long allocId, String newRoomNo) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> {
            Allocation a = lockActive(c, allocId, blockId);
            if (a.roomNo().equalsIgnoreCase(newRoomNo)) {
                throw new ServiceException(a.studentName() + " is already in room " + a.roomNo() + ".");
            }
            AllocationDao.vacate(c, a.allocId());
            Room target = RoomService.lockRoom(c, blockId, newRoomNo);
            requireFreeBed(c, target);
            return AllocationDao.insertActive(c, a.studentId(), blockId, target.roomNo(), s.userId());
        });
    }

    private static Allocation lockActive(Connection c, long allocId, long blockId) throws SQLException {
        Allocation a = AllocationDao.lock(c, allocId).orElseThrow(() -> new ServiceException("Allocation not found."));
        if (a.blockId() != blockId) {
            throw new ServiceException("You can only manage allocations in your own block (BR-4).");
        }
        if (!a.isActive()) {
            throw new ServiceException("This allocation has already been vacated.");
        }
        return a;
    }

    /** BR-1: counted after the room row is locked, so the count cannot change underneath us. */
    private static void requireFreeBed(Connection c, Room room) throws SQLException {
        if (AllocationDao.countActive(c, room.blockId(), room.roomNo()) >= room.capacity()) {
            throw new ServiceException("Room " + room.roomNo() + " (" + room.blockName() + ") is full (BR-1).");
        }
    }
}
