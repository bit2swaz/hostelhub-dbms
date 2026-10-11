package com.hostelhub.service;

import com.hostelhub.dao.ComplaintDao;
import com.hostelhub.dao.RoomDao;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.model.Complaint;
import com.hostelhub.model.ReportSummary;
import com.hostelhub.model.Room;
import com.hostelhub.util.Session;
import java.util.List;

/** FR-11 reports for the warden's own block. */
public class ReportService {

    private final ConnectionFactory db;

    public ReportService(ConnectionFactory db) {
        this.db = db;
    }

    /** From V_ROOM_OCCUPANCY. */
    public List<Room> vacancy(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> RoomDao.listByBlock(c, blockId));
    }

    public List<Complaint> openComplaints(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> ComplaintDao.openForBlock(c, blockId));
    }

    /** From V_OVERDUE_COMPLAINTS (BR-6). */
    public List<Complaint> overdueComplaints(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> ComplaintDao.overdueForBlock(c, blockId));
    }

    public ReportSummary summary(Session s) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> {
            List<Room> rooms = RoomDao.listByBlock(c, blockId);
            int beds = rooms.stream().mapToInt(Room::capacity).sum();
            int occupied = rooms.stream().mapToInt(Room::occupancy).sum();
            return new ReportSummary(rooms.size(), beds, occupied, beds - occupied,
                    ComplaintDao.countOpen(c, blockId), ComplaintDao.countOverdue(c, blockId));
        });
    }
}
