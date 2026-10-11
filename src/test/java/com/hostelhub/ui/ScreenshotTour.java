package com.hostelhub.ui;

import static org.assertj.swing.launcher.ApplicationLauncher.application;

import com.hostelhub.App;
import java.io.File;
import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.fixture.JOptionPaneFixture;

/**
 * PRD 11.4 visual check: launches the real application (App.main, default db.properties, so the
 * seeded database on localhost:1521/XEPDB1) and saves a desktop screenshot of every screen in
 * PRD section 10, plus the main dialogs and messages.
 *
 * <pre>
 * MAVEN_OPTS="--add-opens java.base/java.util=ALL-UNNAMED" \
 * xvfb-run -a -s "-screen 0 1280x800x24" mvn -q exec:java -Dexec.classpathScope=test \
 *     -Dexec.mainClass=com.hostelhub.ui.ScreenshotTour -Dexec.args=docs/screenshots
 * </pre>
 * It raises one complaint, so re-run 99_drop.sql and 01-04 afterwards for a clean demo.
 */
public final class ScreenshotTour {

    private final SwingDriver ui = new SwingDriver(BasicRobot.robotWithCurrentAwtHierarchy());
    private final File dir;

    private ScreenshotTour(File dir) {
        this.dir = dir;
    }

    public static void main(String[] args) {
        File dir = new File(args.length > 0 ? args[0] : "target/screenshots/tour");
        try {
            new ScreenshotTour(dir).run();
            System.out.println("Screenshots saved to " + dir.getAbsolutePath());
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private void shot(String name) {
        ui.shot(dir, name);
    }

    private void run() {
        application(App.class).start();

        FrameFixture login = ui.loginFrame();
        shot("01-login");
        login.textBox("email").setText("arjun.nair@hostelhub.test");
        login.textBox("password").setText("wrong-password");
        login.button("login").click();
        ui.waitForText(login, "error", "Invalid email or password.");
        shot("02-login-error");

        FrameFixture arjun = ui.logIn(login, "arjun.nair@hostelhub.test");
        ui.waitForRows(arjun, "roommates", 2);
        shot("03-student-my-room");
        login = ui.logOut(arjun);

        FrameFixture priya = ui.logIn(login, "priya.sharma@hostelhub.test");
        priya.tabbedPane("tabs").selectTab("My Complaints");
        ui.waitForRows(priya, "complaints", 2);
        shot("04-student-my-complaints");
        priya.button("raise").click();
        DialogFixture raise = ui.dialog();
        ui.until("room loaded", () -> raise.button("ok").target().isEnabled());
        raise.comboBox("category").selectItem(0);
        raise.textBox("description").setText("Bathroom door lock is broken and does not close.");
        shot("05-student-raise-complaint-dialog");
        raise.button("ok").click();
        ui.waitForRows(priya, "complaints", 3);
        shot("06-student-complaint-raised");
        login = ui.logOut(priya);

        FrameFixture karan = ui.logIn(login, "karan.mehta@hostelhub.test");
        ui.waitUntilShowing("emptyState");
        shot("07-student-no-room");
        login = ui.logOut(karan);

        FrameFixture warden = ui.logIn(login, "warden.a@hostelhub.test");
        ui.waitForRows(warden, "rooms", 12);
        ui.until("block loaded", () -> warden.textBox("blockName").target().getText().equals("A Block"));
        shot("08-warden-block-rooms");
        warden.button("addRoom").click();
        DialogFixture room = ui.dialog();
        room.textBox("roomNo").setText("A-304");
        shot("09-warden-add-room-dialog");
        room.button("cancel").click();
        ui.selectRow(warden, "rooms", "A-102");
        warden.button("deleteRoom").click();
        JOptionPaneFixture confirm = warden.optionPane(SwingDriver.WAIT);
        shot("10-warden-delete-room-confirm");
        confirm.yesButton().click();
        JOptionPaneFixture blocked = warden.optionPane(SwingDriver.WAIT);
        shot("11-warden-delete-room-blocked");
        blocked.okButton().click();

        warden.tabbedPane("tabs").selectTab("Students");
        ui.waitForRows(warden, "students", 45);
        shot("12-warden-students");
        warden.textBox("search").setText("sharma");
        warden.button("searchButton").click();
        ui.waitForRows(warden, "students", 1);
        shot("13-warden-students-search");
        warden.button("addStudent").click();
        DialogFixture add = ui.dialog();
        add.textBox("fullName").setText("Rhea Kapoor");
        add.textBox("email").setText("rhea.kapoor@hostelhub.test");
        add.textBox("regNo").setText("25bce20");
        add.button("ok").click();
        ui.waitForText(add, "error", "Registration number must look like 25BCE0231.");
        shot("14-warden-student-dialog-validation");
        add.button("cancel").click();

        warden.tabbedPane("tabs").selectTab("Allocations");
        ui.waitForRows(warden, "allocations", 15);
        shot("15-warden-allocations");
        warden.button("allocate").click();
        DialogFixture allocate = ui.dialog();
        ui.until("pickers loaded", () -> allocate.comboBox("student").target().getItemCount() > 0
                && allocate.comboBox("room").target().getItemCount() > 0);
        shot("16-warden-allocate-dialog");
        allocate.button("cancel").click();
        ui.selectRow(warden, "allocations", "25BCE1045");
        warden.button("transfer").click();
        DialogFixture transfer = ui.dialog();
        ui.until("rooms loaded", () -> transfer.comboBox("room").target().getItemCount() > 0);
        shot("17-warden-transfer-dialog");
        transfer.button("cancel").click();

        warden.tabbedPane("tabs").selectTab("Complaints");
        ui.waitForRows(warden, "complaints", 5);
        shot("18-warden-complaints");
        ui.selectRow(warden, "complaints", "Wi-Fi keeps disconnecting every few minutes in the evening.");
        warden.button("assignStaff").click();
        DialogFixture assign = ui.dialog();
        ui.until("staff loaded", () -> assign.comboBox("staff").target().getItemCount() > 0);
        shot("19-warden-assign-staff-dialog");
        assign.button("cancel").click();
        warden.checkBox("overdueOnly").check();
        ui.waitForRows(warden, "complaints", 2);
        shot("20-warden-complaints-overdue-filter");

        warden.tabbedPane("tabs").selectTab("Reports");
        ui.waitForRows(warden, "overdueTable", 2);
        ui.waitForText(warden, "rooms", "12");
        shot("21-warden-reports");
    }
}
