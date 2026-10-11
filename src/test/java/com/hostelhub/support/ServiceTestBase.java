package com.hostelhub.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.hostelhub.service.AllocationService;
import com.hostelhub.service.AuthService;
import com.hostelhub.service.ComplaintService;
import com.hostelhub.service.ReportService;
import com.hostelhub.service.RoomService;
import com.hostelhub.service.ServiceException;
import com.hostelhub.service.StudentService;
import com.hostelhub.util.Session;
import java.sql.SQLException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;

/** Base for tests that commit through the services. The seed is restored after every test. */
public abstract class ServiceTestBase extends OracleTestBase {

    protected final AuthService auth = new AuthService(DB);
    protected final StudentService students = new StudentService(DB);
    protected final RoomService rooms = new RoomService(DB);
    protected final AllocationService allocations = new AllocationService(DB);
    protected final ComplaintService complaints = new ComplaintService(DB);
    protected final ReportService reports = new ReportService(DB);

    @AfterEach
    void restoreSeed() throws Exception {
        resetData();
    }

    protected Session login(String email) {
        return auth.login(email, SEED_PASSWORD);
    }

    protected Session wardenA() {
        return login("warden.a@hostelhub.test");
    }

    protected Session wardenB() {
        return login("warden.b@hostelhub.test");
    }

    protected Session wardenC() {
        return login("warden.c@hostelhub.test");
    }

    protected static long studentId(String regNo) throws SQLException {
        return queryLong("SELECT user_id FROM student WHERE reg_no = ?", regNo);
    }

    protected static long activeAllocId(String regNo) throws SQLException {
        return queryLong("SELECT a.alloc_id FROM allocation a JOIN student s ON s.user_id = a.student_id "
                + "WHERE s.reg_no = ? AND a.status = 'ACTIVE'", regNo);
    }

    protected static long complaintId(String description) throws SQLException {
        return queryLong("SELECT complaint_id FROM complaint WHERE description = ?", description);
    }

    protected static long staffId(String name) throws SQLException {
        return queryLong("SELECT staff_id FROM staff WHERE full_name = ?", name);
    }

    protected static long categoryId(String name) throws SQLException {
        return queryLong("SELECT category_id FROM category WHERE category_name = ?", name);
    }

    protected static long blockId(String name) throws SQLException {
        return queryLong("SELECT block_id FROM block WHERE block_name = ?", name);
    }

    protected static long occupancy(String roomNo) throws SQLException {
        return queryLong("SELECT occupancy FROM v_room_occupancy WHERE room_no = ?", roomNo);
    }

    /**
     * Asserts the service rejected the call itself, before the database: exact message and no
     * SQLException cause (database rejections always carry one).
     */
    public static void assertServiceRejects(ThrowingCallable call, String message) {
        ServiceException e = catchThrowableOfType(ServiceException.class, call);
        assertThat(e).as("expected ServiceException").isNotNull();
        assertThat(e).hasMessage(message).hasNoCause();
    }
}
