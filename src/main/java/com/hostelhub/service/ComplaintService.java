package com.hostelhub.service;

import com.hostelhub.dao.AllocationDao;
import com.hostelhub.dao.CategoryDao;
import com.hostelhub.dao.ComplaintDao;
import com.hostelhub.dao.StaffDao;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.model.Allocation;
import com.hostelhub.model.Category;
import com.hostelhub.model.Complaint;
import com.hostelhub.model.ComplaintStatus;
import com.hostelhub.model.Staff;
import com.hostelhub.util.Session;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** FR-07 raise, FR-08 assign staff, FR-09 status, FR-10 track; BR-5 state machine, BR-6 overdue flag. */
public class ComplaintService {

    private final ConnectionFactory db;

    public ComplaintService(ConnectionFactory db) {
        this.db = db;
    }

    public List<Category> categories() {
        return Tx.run(db, CategoryDao::all);
    }

    /** FR-07: only with an ACTIVE allocation; the room is taken from it. */
    public long raise(Session s, long categoryId, String description) {
        long me = Validators.studentId(s);
        String d = Validators.description(description);
        return Tx.run(db, c -> {
            Allocation a = AllocationDao.activeFor(c, me).orElseThrow(() ->
                    new ServiceException("You need an active room allocation to raise a complaint."));
            return ComplaintDao.insert(c, me, a.blockId(), a.roomNo(), categoryId, d);
        });
    }

    /** FR-10. */
    public List<Complaint> myComplaints(Session s) {
        long me = Validators.studentId(s);
        return Tx.run(db, c -> ComplaintDao.forStudent(c, me));
    }

    public List<Complaint> blockComplaints(Session s, ComplaintStatus status, boolean overdueOnly) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> ComplaintDao.forBlock(c, blockId, status, overdueOnly));
    }

    /** FR-08 picker: staff whose category matches the complaint. */
    public List<Staff> eligibleStaff(Session s, long complaintId) {
        long blockId = Validators.wardenBlock(s);
        return Tx.run(db, c -> StaffDao.byCategory(c, lockOwn(c, complaintId, blockId).categoryId()));
    }

    /** FR-08: own block only, staff category must match, not after RESOLVED. */
    public void assignStaff(Session s, long complaintId, long staffId) {
        long blockId = Validators.wardenBlock(s);
        Tx.run(db, c -> {
            Complaint cp = lockOwn(c, complaintId, blockId);
            if (cp.status() == ComplaintStatus.RESOLVED) {
                throw new ServiceException("A resolved complaint cannot be reassigned (BR-5).");
            }
            Staff st = StaffDao.find(c, staffId).orElseThrow(() -> new ServiceException("Staff member not found."));
            if (st.categoryId() != cp.categoryId()) {
                throw new ServiceException(st.fullName() + " handles " + st.categoryName() + ", not "
                        + cp.categoryName() + " complaints.");
            }
            return ComplaintDao.assignStaff(c, complaintId, staffId);
        });
    }

    /** FR-09 / BR-5: forward one step only, OPEN -> IN_PROGRESS -> RESOLVED. */
    public void updateStatus(Session s, long complaintId, ComplaintStatus target) {
        long blockId = Validators.wardenBlock(s);
        Tx.run(db, c -> {
            Complaint cp = lockOwn(c, complaintId, blockId);
            if (cp.status() == ComplaintStatus.RESOLVED) {
                throw new ServiceException("Complaint #" + complaintId + " is already resolved (BR-5).");
            }
            if (target != cp.status().next()) {
                throw new ServiceException("Status can only move forward one step: OPEN -> IN_PROGRESS -> RESOLVED (BR-5).");
            }
            if (cp.staffId() == null) {
                throw new ServiceException("Assign staff before starting work on this complaint.");
            }
            return ComplaintDao.updateStatus(c, complaintId, target);
        });
    }

    private static Complaint lockOwn(Connection c, long complaintId, long blockId) throws SQLException {
        Complaint cp = ComplaintDao.lock(c, complaintId).orElseThrow(() -> new ServiceException("Complaint not found."));
        if (cp.blockId() != blockId) {
            throw new ServiceException("You can only manage complaints in your own block (BR-4).");
        }
        return cp;
    }
}
