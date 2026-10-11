package com.hostelhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.model.Complaint;
import com.hostelhub.model.ComplaintStatus;
import com.hostelhub.model.ReportSummary;
import com.hostelhub.model.Room;
import com.hostelhub.support.OracleTestBase;
import com.hostelhub.support.ServiceTestBase;
import com.hostelhub.util.Session;
import org.junit.jupiter.api.Test;

/** FR-11. Read-only. */
class ReportServiceTest extends OracleTestBase {

    private final ReportService reports = new ReportService(DB);
    private final Session wardenA = new AuthService(DB).login("warden.a@hostelhub.test", SEED_PASSWORD);

    @Test
    void fr11_summaryCountsForOwnBlock() {
        assertThat(reports.summary(wardenA)).isEqualTo(new ReportSummary(12, 28, 14, 14, 3, 2));
    }

    @Test
    void fr11_vacancyReportComesFromOccupancyView() {
        assertThat(reports.vacancy(wardenA)).hasSize(12)
                .filteredOn(r -> r.roomNo().equals("A-201")).singleElement()
                .satisfies(r -> assertThat(r.vacancies()).isEqualTo(1));
        assertThat(reports.vacancy(wardenA).stream().mapToInt(Room::vacancies).sum()).isEqualTo(14);
    }

    @Test
    void fr11_openAndOverdueComplaints() {
        assertThat(reports.openComplaints(wardenA)).hasSize(3).noneMatch(c -> c.status() == ComplaintStatus.RESOLVED);
        assertThat(reports.overdueComplaints(wardenA))
                .extracting(Complaint::categoryName, Complaint::daysOverdue)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Electrical", 3),
                        org.assertj.core.groups.Tuple.tuple("Internet", 2));
    }

    @Test
    void fr11_reportsAreWardenOnly() {
        Session student = new AuthService(DB).login("arjun.nair@hostelhub.test", SEED_PASSWORD);
        ServiceTestBase.assertServiceRejects(() -> reports.summary(student), "Only wardens can do this.");
    }
}
