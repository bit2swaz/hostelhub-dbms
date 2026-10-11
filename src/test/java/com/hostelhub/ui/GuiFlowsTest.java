package com.hostelhub.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.App;
import com.hostelhub.service.Services;
import com.hostelhub.support.ServiceTestBase;
import java.io.File;
import java.util.regex.Pattern;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.edt.FailOnThreadViolationRepaintManager;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JOptionPaneFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

/**
 * PRD 11.3 GUI flows with AssertJ Swing against the seeded Testcontainers database.
 * Needs a display: run under xvfb-run on Linux, or skip with -DexcludedGroups=gui.
 */
@Tag("gui")
class GuiFlowsTest extends ServiceTestBase {

    private static final File SHOTS = new File("target/screenshots");
    private SwingDriver ui;
    private FrameFixture login;
    private String testName;

    @BeforeAll
    static void failOnEdtViolations() {
        FailOnThreadViolationRepaintManager.install();
    }

    @BeforeEach
    void openApp(TestInfo info) {
        testName = info.getTestMethod().orElseThrow().getName();
        ui = new SwingDriver(BasicRobot.robotWithNewAwtHierarchy());
        GuiActionRunner.execute(() -> {
            App.installLookAndFeel();
            new LoginFrame(Services.create(DB)).setVisible(true);
        });
        login = ui.loginFrame();
    }

    @AfterEach
    void closeApp() {
        ui.shot(SHOTS, testName);
        ui.robot().cleanUp();
    }

    @Test
    void fr01_badLoginShowsError() {
        login.textBox("email").setText("arjun.nair@hostelhub.test");
        login.textBox("password").setText("not-my-password");
        login.button("login").click();
        ui.waitForText(login, "error", "Invalid email or password.");
        login.requireVisible();
    }

    @Test
    void fr07_fr10_studentRaisesComplaintAndSeesItInTheTable() {
        FrameFixture student = ui.logIn(login, "arjun.nair@hostelhub.test");
        student.tabbedPane("tabs").selectTab("My Complaints");
        ui.waitForRows(student, "complaints", 2);
        student.button("raise").click();
        DialogFixture d = ui.dialog();
        ui.until("room loaded", () -> d.button("ok").target().isEnabled());
        d.label("room").requireText("A-103 (A Block)");
        d.comboBox("category").selectItem(Pattern.compile("Electrical.*"));
        d.textBox("description").setText("Short");
        d.button("ok").click();
        ui.waitForText(d, "error", "Description must be at least 10 characters.");
        d.textBox("description").setText("Switch board near the door is loose.");
        d.button("ok").click();
        ui.waitForRows(student, "complaints", 3);
        ui.selectRow(student, "complaints", "Switch board near the door is loose.");
        assertThat(student.table("complaints").selectionValue()).isNotNull();
    }

    @Test
    void fr06_unallocatedStudentSeesEmptyState() {
        ui.logIn(login, "karan.mehta@hostelhub.test");
        ui.waitUntilShowing("emptyState");
    }

    @Test
    void fr04_wardenAllocatesAStudentToARoom() throws Exception {
        FrameFixture warden = ui.logIn(login, "warden.a@hostelhub.test");
        warden.tabbedPane("tabs").selectTab("Allocations");
        ui.waitForRows(warden, "allocations", 15);
        warden.button("allocate").click();
        DialogFixture d = ui.dialog();
        ui.until("pickers loaded", () -> d.comboBox("student").target().getItemCount() == 4
                && d.comboBox("room").target().getItemCount() > 0);
        d.comboBox("student").selectItem(Pattern.compile("25BIT0633.*"));
        d.comboBox("room").selectItem(Pattern.compile("A-202 .*"));
        d.button("ok").click();
        ui.waitForRows(warden, "allocations", 16);
        assertThat(queryLong("SELECT COUNT(*) FROM allocation a JOIN student s ON s.user_id = a.student_id "
                + "WHERE s.reg_no = '25BIT0633' AND a.room_no = 'A-202' AND a.status = 'ACTIVE'")).isEqualTo(1);
    }

    @Test
    void br1_allocatingToARoomThatJustFilledShowsTheBr1Message() throws Exception {
        FrameFixture warden = ui.logIn(login, "warden.a@hostelhub.test");
        warden.tabbedPane("tabs").selectTab("Allocations");
        warden.button("allocate").click();
        DialogFixture d = ui.dialog();
        ui.until("pickers loaded", () -> d.comboBox("student").target().getItemCount() == 4
                && d.comboBox("room").target().getItemCount() > 0);
        d.comboBox("student").selectItem(Pattern.compile("25BIT0633.*"));
        d.comboBox("room").selectItem(Pattern.compile("A-201 .*1 of 3 free"));
        // Another warden session takes the last bed of A-201 while this dialog is open.
        allocations.allocate(wardenA(), studentId("25BCE1420"), "A-201");
        d.button("ok").click();
        ui.waitForText(d, "error", "Room A-201 (A Block) is full (BR-1).");
        d.requireVisible();
        assertThat(occupancy("A-201")).isEqualTo(3);
    }

    @Test
    void fr08_fr09_wardenAssignsStaffAndResolvesAComplaint() throws Exception {
        String washbasin = "Washbasin drain is blocked and water does not flow out.";
        FrameFixture warden = ui.logIn(login, "warden.c@hostelhub.test");
        warden.tabbedPane("tabs").selectTab("Complaints");
        ui.waitForRows(warden, "complaints", 5);
        ui.selectRow(warden, "complaints", washbasin);
        warden.button("assignStaff").click();
        DialogFixture d = ui.dialog();
        ui.until("staff loaded", () -> d.comboBox("staff").target().getItemCount() == 2);
        d.comboBox("staff").selectItem(Pattern.compile("Joseph Thomas.*"));
        d.button("ok").click();
        ui.until("staff shown", () -> rowText(warden, washbasin).contains("Joseph Thomas"));

        for (String next : new String[] {"IN_PROGRESS", "RESOLVED"}) {
            ui.selectRow(warden, "complaints", washbasin);
            warden.button("advanceStatus").click();
            JOptionPaneFixture confirm = warden.optionPane(SwingDriver.WAIT);
            confirm.requireMessage(Pattern.compile(".* to " + next + "\\?"));
            confirm.yesButton().click();
            ui.until("status " + next, () -> rowText(warden, washbasin).contains(next));
        }
        assertThat(queryLong("SELECT COUNT(*) FROM complaint WHERE description = ? AND status = 'RESOLVED' "
                + "AND resolved_on IS NOT NULL AND staff_id = ?", washbasin, staffId("Joseph Thomas"))).isEqualTo(1);
    }

    @Test
    void fr02_deletingARoomWithHistoryIsBlockedWithAMessage() throws Exception {
        FrameFixture warden = ui.logIn(login, "warden.a@hostelhub.test");
        ui.waitForRows(warden, "rooms", 12);
        ui.selectRow(warden, "rooms", "A-102");
        warden.button("deleteRoom").click();
        warden.optionPane(SwingDriver.WAIT).requireMessage("Delete room A-102? This cannot be undone.").yesButton().click();
        JOptionPaneFixture blocked = warden.optionPane(SwingDriver.WAIT);
        blocked.requireErrorMessage()
                .requireMessage("Room A-102 has allocation or complaint history and cannot be deleted.");
        ui.shot(SHOTS, testName);
        blocked.okButton().click();
        assertThat(queryLong("SELECT COUNT(*) FROM room WHERE room_no = 'A-102'")).isEqualTo(1);
    }

    @Test
    void fr05_wardenTransfersThenVacatesKeepingHistory() throws Exception {
        FrameFixture warden = ui.logIn(login, "warden.a@hostelhub.test");
        warden.tabbedPane("tabs").selectTab("Allocations");
        ui.waitForRows(warden, "allocations", 15);
        ui.selectRow(warden, "allocations", "25BCE1045");
        warden.button("transfer").click();
        DialogFixture d = ui.dialog();
        ui.until("rooms loaded", () -> d.comboBox("room").target().getItemCount() > 0);
        d.comboBox("room").selectItem(Pattern.compile("A-302 .*"));
        d.button("ok").click();
        ui.waitForRows(warden, "allocations", 16);
        assertThat(queryLong("SELECT COUNT(*) FROM allocation a JOIN student s ON s.user_id = a.student_id "
                + "WHERE s.reg_no = '25BCE1045' AND a.room_no = 'A-302' AND a.status = 'ACTIVE'")).isEqualTo(1);

        ui.selectRow(warden, "allocations", "25BCE1045"); // newest first: the new ACTIVE row
        warden.button("vacate").click();
        warden.optionPane(SwingDriver.WAIT).yesButton().click();
        warden.comboBox("statusFilter").selectItem("Vacated");
        ui.waitForRows(warden, "allocations", 3);
        assertThat(queryLong("SELECT COUNT(*) FROM allocation a JOIN student s ON s.user_id = a.student_id "
                + "WHERE s.reg_no = '25BCE1045' AND a.status = 'VACATED' AND a.end_date IS NOT NULL")).isEqualTo(2);
    }

    @Test
    void fr02_fr03_wardenEditsBlockAddsRoomAndRegistersThenDeletesAStudent() throws Exception {
        FrameFixture warden = ui.logIn(login, "warden.a@hostelhub.test");
        ui.waitForRows(warden, "rooms", 12);
        ui.until("block loaded", () -> warden.textBox("blockName").target().getText().equals("A Block"));
        warden.spinner("blockFloors").select(5);
        warden.button("saveBlock").click();
        ui.waitForText(warden, "blockError", "Saved.");
        warden.button("addRoom").click();
        DialogFixture room = ui.dialog();
        room.textBox("roomNo").setText("A-401");
        room.spinner("floor").select(4);
        room.comboBox("type").selectItem("DOUBLE");
        room.label("capacity").requireText("2 beds");
        room.button("ok").click();
        ui.waitForRows(warden, "rooms", 13);

        warden.tabbedPane("tabs").selectTab("Students");
        ui.waitForRows(warden, "students", 45);
        warden.button("addStudent").click();
        DialogFixture add = ui.dialog();
        add.textBox("fullName").setText("Rhea Kapoor");
        add.textBox("email").setText("rhea.kapoor@hostelhub.test");
        add.textBox("phone").setText("9876543210");
        add.textBox("regNo").setText("25BCE2001");
        add.comboBox("gender").selectItem("Female");
        add.textBox("initialPassword").setText("Welcome@2025");
        add.button("ok").click();
        ui.waitForRows(warden, "students", 46);
        ui.selectRow(warden, "students", "25BCE2001");
        warden.button("deleteStudent").click();
        warden.optionPane(SwingDriver.WAIT).yesButton().click();
        ui.waitForRows(warden, "students", 45);
        assertThat(queryLong("SELECT COUNT(*) FROM room WHERE room_no = 'A-401' AND capacity = 2")).isEqualTo(1);
        assertThat(queryLong("SELECT num_floors FROM block WHERE block_name = 'A Block'")).isEqualTo(5);
    }

    @Test
    void fr02_fr03_wardenEditsARoomAndAStudent() throws Exception {
        FrameFixture warden = ui.logIn(login, "warden.a@hostelhub.test");
        ui.waitForRows(warden, "rooms", 12);
        ui.selectRow(warden, "rooms", "A-103");
        warden.button("editRoom").click();
        DialogFixture shrink = ui.dialog();
        shrink.textBox("roomNo").requireDisabled();
        shrink.comboBox("type").selectItem("SINGLE");
        shrink.button("ok").click();
        ui.waitForText(shrink, "error", "Room A-103 has 3 occupants; a SINGLE room holds only 1 (BR-1).");
        shrink.button("cancel").click();

        ui.selectRow(warden, "rooms", "A-002");
        warden.button("editRoom").click();
        DialogFixture room = ui.dialog();
        room.comboBox("type").selectItem("DOUBLE");
        room.button("ok").click();
        ui.until("A-002 is DOUBLE", () -> rowOf(warden, "rooms", "A-002").contains("DOUBLE"));

        warden.tabbedPane("tabs").selectTab("Students");
        ui.waitForRows(warden, "students", 45);
        ui.selectRow(warden, "students", "25BIT0633");
        warden.button("editStudent").click();
        DialogFixture edit = ui.dialog();
        edit.textBox("regNo").requireText("25BIT0633");
        edit.textBox("fullName").setText("Naveen K");
        edit.spinner("year").select(2);
        edit.button("ok").click();
        ui.until("name changed", () -> rowOf(warden, "students", "25BIT0633").contains("Naveen K"));
        assertThat(queryLong("SELECT capacity FROM room WHERE room_no = 'A-002'")).isEqualTo(2);
        assertThat(queryLong("SELECT year_of_study FROM student WHERE reg_no = '25BIT0633'")).isEqualTo(2);
    }

    /** All cell values of the first row containing text, joined. */
    private static String rowOf(FrameFixture f, String table, String text) {
        var t = f.table(table).target();
        for (int r = 0; r < t.getRowCount(); r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < t.getColumnCount(); c++) {
                sb.append(t.getValueAt(r, c)).append(" | ");
            }
            if (sb.indexOf(text) >= 0) {
                return sb.toString();
            }
        }
        return "";
    }

    private static String rowText(FrameFixture f, String text) {
        return rowOf(f, "complaints", text);
    }
}
