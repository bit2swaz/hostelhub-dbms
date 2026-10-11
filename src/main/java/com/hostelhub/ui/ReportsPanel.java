package com.hostelhub.ui;

import com.hostelhub.model.Complaint;
import com.hostelhub.model.ReportSummary;
import com.hostelhub.model.Room;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** FR-11: summary counts, room vacancy (V_ROOM_OCCUPANCY), open and overdue (V_OVERDUE_COMPLAINTS) complaints. */
public class ReportsPanel extends JPanel implements Refreshable {

    private final Services services;
    private final Session session;
    private final JLabel rooms = number("rooms");
    private final JLabel beds = number("beds");
    private final JLabel occupied = number("occupied");
    private final JLabel vacant = number("vacant");
    private final JLabel open = number("open");
    private final JLabel overdue = number("overdue");
    private final ListTableModel<Room> vacancy = new ListTableModel<>(r -> new Object[] {
        r.roomNo(), r.floorNo(), r.roomType().name(), r.capacity(), r.occupancy(), r.vacancies()},
            "Room", "Floor", "Type", "Capacity", "Occupancy", "Vacancies");
    private final ListTableModel<Complaint> openModel = new ListTableModel<>(c -> new Object[] {
        c.complaintId(), c.roomNo(), c.studentName(), c.categoryName(), c.status().name(),
        c.staffName() == null ? "Not assigned" : c.staffName(), Ui.date(c.raisedOn()), c.overdue() ? "Yes" : ""},
            "#", "Room", "Student", "Category", "Status", "Staff", "Raised on", "Overdue");
    private final ListTableModel<Complaint> overdueModel = new ListTableModel<>(c -> new Object[] {
        c.complaintId(), c.roomNo(), c.categoryName(), c.status().name(),
        c.staffName() == null ? "Not assigned" : c.staffName(), c.slaDays(), Ui.date(c.raisedOn()), c.daysOverdue()},
            "#", "Room", "Category", "Status", "Staff", "SLA days", "Raised on", "Days overdue");

    public ReportsPanel(Services services, Session session) {
        super(new BorderLayout(0, 12));
        this.services = services;
        this.session = session;
        Ui.pad(this, 16);

        JPanel cards = new JPanel(new GridLayout(1, 6, 10, 0));
        cards.add(card("Rooms", rooms));
        cards.add(card("Beds", beds));
        cards.add(card("Occupied beds", occupied));
        cards.add(card("Vacant beds", vacant));
        cards.add(card("Open complaints", open));
        cards.add(card("Overdue complaints", overdue));

        JScrollPane vacancyPane = titled("Room vacancy (V_ROOM_OCCUPANCY)", Ui.table("vacancyTable", vacancy));
        vacancyPane.setPreferredSize(new java.awt.Dimension(480, 0));
        JPanel right = new JPanel(new GridLayout(2, 1, 0, 12));
        right.add(titled("Open complaints", Ui.table("openTable", openModel, Complaint::overdue)));
        right.add(titled("Overdue complaints (V_OVERDUE_COMPLAINTS)", Ui.table("overdueTable", overdueModel)));
        JPanel tables = new JPanel(new BorderLayout(12, 0));
        tables.add(vacancyPane, BorderLayout.WEST);
        tables.add(right, BorderLayout.CENTER);

        add(cards, BorderLayout.NORTH);
        add(tables, BorderLayout.CENTER);
    }

    private static JLabel number(String name) {
        JLabel l = new JLabel("-");
        l.setName(name);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 26f));
        return l;
    }

    private static JPanel card(String caption, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xDADCE0)),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JLabel c = new JLabel(caption);
        c.setForeground(Ui.MUTED);
        p.add(c, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    private static JScrollPane titled(String title, JComponent table) {
        JScrollPane s = new JScrollPane(table);
        s.setBorder(BorderFactory.createTitledBorder(title));
        return s;
    }

    @Override
    public void refresh() {
        Ui.async(this, () -> services.reports().summary(session), this::show);
        Ui.async(this, () -> services.reports().vacancy(session), vacancy::setRows);
        Ui.async(this, () -> services.reports().openComplaints(session), openModel::setRows);
        Ui.async(this, () -> services.reports().overdueComplaints(session), overdueModel::setRows);
    }

    private void show(ReportSummary s) {
        rooms.setText(String.valueOf(s.rooms()));
        beds.setText(String.valueOf(s.beds()));
        occupied.setText(String.valueOf(s.occupied()));
        vacant.setText(String.valueOf(s.vacant()));
        open.setText(String.valueOf(s.openComplaints()));
        overdue.setText(String.valueOf(s.overdueComplaints()));
        overdue.setForeground(s.overdueComplaints() > 0 ? Ui.ERROR : getForeground());
    }
}
