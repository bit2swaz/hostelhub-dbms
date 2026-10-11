package com.hostelhub.ui;

import static org.assertj.swing.finder.WindowFinder.findDialog;
import static org.assertj.swing.finder.WindowFinder.findFrame;

import java.awt.Component;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.assertj.swing.core.Robot;
import org.assertj.swing.edt.GuiActionRunner;
import org.assertj.swing.fixture.DialogFixture;
import org.assertj.swing.fixture.FrameFixture;
import org.assertj.swing.image.ScreenshotTaker;
import org.assertj.swing.timing.Condition;
import org.assertj.swing.timing.Pause;
import org.assertj.swing.timing.Timeout;

/** AssertJ Swing steps shared by the GUI tests and the screenshot tour. Every read goes through the EDT. */
final class SwingDriver {

    static final Timeout WAIT = Timeout.timeout(20, TimeUnit.SECONDS);
    static final String PASSWORD = "Hostel@123";

    private final Robot robot;
    private final ScreenshotTaker camera = new ScreenshotTaker();

    SwingDriver(Robot robot) {
        this.robot = robot;
    }

    Robot robot() {
        return robot;
    }

    FrameFixture loginFrame() {
        return findFrame("loginFrame").withTimeout(20, TimeUnit.SECONDS).using(robot);
    }

    FrameFixture logIn(FrameFixture login, String email) {
        login.textBox("email").setText(email);
        login.textBox("password").setText(PASSWORD);
        login.button("login").click();
        return findFrame("dashboard").withTimeout(20, TimeUnit.SECONDS).using(robot);
    }

    FrameFixture logOut(FrameFixture dashboard) {
        dashboard.button("logout").click();
        return loginFrame();
    }

    DialogFixture dialog() {
        return findDialog("dialog").withTimeout(10, TimeUnit.SECONDS).using(robot);
    }

    void until(String description, Supplier<Boolean> onEdt) {
        Pause.pause(new Condition(description) {
            @Override
            public boolean test() {
                return GuiActionRunner.execute(onEdt::get);
            }
        }, WAIT);
    }

    /** Waits until the table has exactly n rows (any rows when n is -1). */
    void waitForRows(FrameFixture f, String table, int n) {
        JTable t = f.table(table).target();
        until(table + " has " + (n < 0 ? "rows" : n + " rows"), () -> n < 0 ? t.getRowCount() > 0 : t.getRowCount() == n);
    }

    void waitForText(FrameFixture f, String label, String text) {
        JLabel l = f.label(label).target();
        until(label + " = " + text, () -> text.equals(l.getText()));
    }

    void waitForText(DialogFixture d, String label, String text) {
        JLabel l = d.label(label).target();
        until(label + " = " + text, () -> text.equals(l.getText()));
    }

    /** Waits until a named component, possibly in a hidden card, is on screen. */
    void waitUntilShowing(String name) {
        until(name + " showing", () -> robot.finder().findAll(c -> name.equals(c.getName())).stream()
                .anyMatch(Component::isShowing));
    }

    /** Selects the first row with a cell whose text equals cellText. */
    void selectRow(FrameFixture f, String table, String cellText) {
        JTable t = f.table(table).target();
        int row = GuiActionRunner.execute(() -> {
            for (int r = 0; r < t.getRowCount(); r++) {
                for (int c = 0; c < t.getColumnCount(); c++) {
                    if (cellText.equals(String.valueOf(t.getValueAt(r, c)))) {
                        return r;
                    }
                }
            }
            return -1;
        });
        if (row < 0) {
            throw new AssertionError("No row in " + table + " contains '" + cellText + "'");
        }
        f.table(table).selectRows(row);
    }

    /** Saves the whole (virtual) screen, so dialogs and option panes are included. */
    void shot(File dir, String name) {
        robot.waitForIdle();
        Pause.pause(300);
        dir.mkdirs();
        File f = new File(dir, name + ".png");
        f.delete();
        camera.saveDesktopAsPng(f.getPath());
    }
}
