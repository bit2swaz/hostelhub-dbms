package com.hostelhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.model.Complaint;
import com.hostelhub.model.ComplaintStatus;
import com.hostelhub.model.Staff;
import com.hostelhub.support.ServiceTestBase;
import com.hostelhub.util.Session;
import java.util.List;
import org.junit.jupiter.api.Test;

/** FR-07 to FR-10. */
class ComplaintServiceTest extends ServiceTestBase {

    private static final String WASHBASIN = "Washbasin drain is blocked and water does not flow out.";

    @Test
    void fr07_raiseUsesTheStudentsActiveRoom() throws Exception {
        Session arjun = login("arjun.nair@hostelhub.test");
        long id = complaints.raise(arjun, categoryId("Electrical"), "  Switch board in the room is loose.  ");
        Complaint c = complaints.myComplaints(arjun).stream().filter(x -> x.complaintId() == id).findFirst().orElseThrow();
        assertThat(c.roomNo()).isEqualTo("A-103");
        assertThat(c.blockName()).isEqualTo("A Block");
        assertThat(c.description()).isEqualTo("Switch board in the room is loose.");
        assertThat(c.status()).isEqualTo(ComplaintStatus.OPEN);
        assertThat(c.staffId()).isNull();
        assertThat(c.overdue()).isFalse();
    }

    @Test
    void fr07_raiseRules() throws Exception {
        long cat = categoryId("Plumbing");
        assertServiceRejects(() -> complaints.raise(login("karan.mehta@hostelhub.test"), cat, "Tap is leaking badly."),
                "You need an active room allocation to raise a complaint.");
        assertServiceRejects(() -> complaints.raise(login("arjun.nair@hostelhub.test"), cat, "  Leaking  "),
                "Description must be at least 10 characters.");
        assertServiceRejects(() -> complaints.raise(wardenA(), cat, "Tap is leaking badly."),
                "Only students can do this.");
    }

    @Test
    void fr10_trackOwnComplaintsWithOverdueFlag() {
        List<Complaint> mine = complaints.myComplaints(login("priya.sharma@hostelhub.test"));
        assertThat(mine).extracting(Complaint::description, Complaint::status, Complaint::staffName, Complaint::overdue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Tube light in the room flickers constantly.",
                                ComplaintStatus.OPEN, "Sunil Das", true),
                        org.assertj.core.groups.Tuple.tuple("Study table drawer is jammed and will not open.",
                                ComplaintStatus.RESOLVED, "Babu Rajan", false));
        assertThat(mine.get(1).resolvedOn()).isNotNull();
    }

    @Test
    void wardenSeesOnlyOwnBlockWithFilters() {
        Session w = wardenA();
        assertThat(complaints.blockComplaints(w, null, false)).hasSize(5).allMatch(c -> c.blockName().equals("A Block"));
        assertThat(complaints.blockComplaints(w, ComplaintStatus.OPEN, false)).hasSize(2);
        assertThat(complaints.blockComplaints(w, null, true)).hasSize(2).allMatch(Complaint::overdue);
        assertThat(complaints.blockComplaints(w, ComplaintStatus.RESOLVED, true)).isEmpty();
    }

    @Test
    void fr08_staffPickerAndAssignmentAreCategoryMatched() throws Exception {
        Session w = wardenC();
        long id = complaintId(WASHBASIN);
        assertThat(complaints.eligibleStaff(w, id)).extracting(Staff::fullName)
                .containsExactly("Joseph Thomas", "Mahesh Gowda");
        complaints.assignStaff(w, id, staffId("Mahesh Gowda"));
        assertThat(queryLong("SELECT staff_id FROM complaint WHERE complaint_id = ?", id)).isEqualTo(staffId("Mahesh Gowda"));
    }

    @Test
    void fr08_wrongCategoryStaffIsRejected() throws Exception {
        long id = complaintId(WASHBASIN);
        long ravi = staffId("Ravi Kumar");
        assertServiceRejects(() -> complaints.assignStaff(wardenC(), id, ravi),
                "Ravi Kumar handles Electrical, not Plumbing complaints.");
    }

    @Test
    void fr09_statusMovesForwardAndResolvedSetsResolvedOn() throws Exception {
        Session w = wardenC();
        long id = complaintId(WASHBASIN);
        complaints.assignStaff(w, id, staffId("Joseph Thomas"));
        complaints.updateStatus(w, id, ComplaintStatus.IN_PROGRESS);
        assertThat(queryLong("SELECT COUNT(*) FROM complaint WHERE complaint_id = ? AND status = 'IN_PROGRESS' "
                + "AND resolved_on IS NULL", id)).isEqualTo(1);
        complaints.updateStatus(w, id, ComplaintStatus.RESOLVED);
        assertThat(queryLong("SELECT COUNT(*) FROM complaint WHERE complaint_id = ? AND status = 'RESOLVED' "
                + "AND resolved_on >= raised_on AND SYSDATE - resolved_on < 1/24", id)).isEqualTo(1);
        // BR-6: resolving removes it from the overdue view.
        assertThat(queryLong("SELECT COUNT(*) FROM v_overdue_complaints WHERE complaint_id = ?", id)).isZero();
    }

    @Test
    void fr09_workCannotStartWithoutStaff() throws Exception {
        long id = complaintId(WASHBASIN);
        assertServiceRejects(() -> complaints.updateStatus(wardenC(), id, ComplaintStatus.IN_PROGRESS),
                "Assign staff before starting work on this complaint.");
    }

    @Test
    void fr08_resolvedComplaintCannotBeReassigned() throws Exception {
        long id = complaintId("Bathroom tap is leaking continuously.");
        long joseph = staffId("Joseph Thomas");
        assertServiceRejects(() -> complaints.assignStaff(wardenA(), id, joseph),
                "A resolved complaint cannot be reassigned (BR-5).");
    }
}
