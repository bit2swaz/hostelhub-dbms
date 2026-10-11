package com.hostelhub.service;

import com.hostelhub.dao.AllocationDao;
import com.hostelhub.dao.RoomDao;
import com.hostelhub.dao.StudentDao;
import com.hostelhub.dao.UserDao;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.model.Allocation;
import com.hostelhub.model.MyRoom;
import com.hostelhub.model.Role;
import com.hostelhub.model.Room;
import com.hostelhub.model.Student;
import com.hostelhub.util.PasswordHasher;
import com.hostelhub.util.Session;
import java.util.List;
import java.util.Optional;

/** FR-03 student records (warden) and FR-06 my room (student). */
public class StudentService {

    private final ConnectionFactory db;

    public StudentService(ConnectionFactory db) {
        this.db = db;
    }

    public List<Student> search(Session s, String text) {
        Validators.wardenBlock(s);
        return Tx.run(db, c -> StudentDao.search(c, text));
    }

    /** Creates HOSTEL_USER + STUDENT in one transaction. */
    public Student create(Session s, Student form, String initialPassword) {
        Validators.wardenBlock(s);
        Student st = Validators.normalize(form);
        Validators.student(st);
        Validators.password(initialPassword);
        String hash = PasswordHasher.hash(initialPassword);
        return Tx.run(db, c -> {
            checkUnique(c, st, 0);
            long id = UserDao.insert(c, st.fullName(), st.email(), st.phone(), hash, Role.STUDENT);
            StudentDao.insert(c, id, st);
            return StudentDao.find(c, id).orElseThrow();
        });
    }

    public Student update(Session s, Student form) {
        Validators.wardenBlock(s);
        Student st = Validators.normalize(form);
        Validators.student(st);
        return Tx.run(db, c -> {
            Student existing = StudentDao.find(c, st.userId())
                    .orElseThrow(() -> new ServiceException("Student not found."));
            checkUnique(c, st, st.userId());
            if (!existing.gender().equals(st.gender()) && AllocationDao.activeFor(c, st.userId()).isPresent()) {
                throw new ServiceException("Cannot change gender while the student has an active room allocation (BR-3).");
            }
            UserDao.update(c, st.userId(), st.fullName(), st.email(), st.phone());
            StudentDao.update(c, st);
            return StudentDao.find(c, st.userId()).orElseThrow();
        });
    }

    /** Deletes a student with no allocation or complaint history (records are kept for audit). */
    public void delete(Session s, long studentId) {
        Validators.wardenBlock(s);
        Tx.run(db, c -> {
            Student st = StudentDao.find(c, studentId).orElseThrow(() -> new ServiceException("Student not found."));
            if (StudentDao.hasHistory(c, studentId)) {
                throw new ServiceException("Student " + st.regNo()
                        + " has allocation or complaint history and cannot be deleted.");
            }
            return UserDao.delete(c, studentId);
        });
    }

    /** FR-06: the student's current room and roommates, or empty when unallocated. */
    public Optional<MyRoom> myRoom(Session s) {
        long me = Validators.studentId(s);
        return Tx.run(db, c -> {
            Optional<Allocation> a = AllocationDao.activeFor(c, me);
            if (a.isEmpty()) {
                return Optional.empty();
            }
            Room r = RoomDao.find(c, a.get().blockId(), a.get().roomNo()).orElseThrow();
            List<Student> roommates = StudentDao.inRoom(c, r.blockId(), r.roomNo()).stream()
                    .filter(st -> st.userId() != me).toList();
            return Optional.of(new MyRoom(r.blockName(), r.roomNo(), r.floorNo(), r.roomType(), r.capacity(),
                    a.get().startDate(), roommates));
        });
    }

    private static void checkUnique(java.sql.Connection c, Student st, long exceptId) throws java.sql.SQLException {
        if (UserDao.emailTaken(c, st.email(), exceptId)) {
            throw new ServiceException("That email address is already registered.");
        }
        if (StudentDao.regNoTaken(c, st.regNo(), exceptId)) {
            throw new ServiceException("That registration number is already registered.");
        }
    }
}
