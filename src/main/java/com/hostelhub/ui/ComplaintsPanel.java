package com.hostelhub.ui;

import com.hostelhub.model.Complaint;
import com.hostelhub.model.ComplaintStatus;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** FR-08 assign staff, FR-09 advance status; filtered to the warden's block (BR-4). */
public class ComplaintsPanel extends JPanel implements Refreshable {

    private final Services services;
    private final Session session;
    private final JComboBox<Ui.Choice<ComplaintStatus>> status = new JComboBox<>();
    private final JCheckBox overdueOnly = new JCheckBox("Overdue only");
    private final ListTableModel<Complaint> model = new ListTableModel<>(c -> new Object[] {
        c.complaintId(), c.roomNo(), c.studentName(), c.categoryName(), c.description(), c.status().name(),
        c.staffName() == null ? "Not assigned" : c.staffName(), Ui.date(c.raisedOn()), Ui.date(c.resolvedOn()),
        c.overdue() ? "Yes (" + c.daysOverdue() + " d)" : ""},
            "#", "Room", "Student", "Category", "Description", "Status", "Staff", "Raised on", "Resolved on", "Overdue");
    private final JTable table = Ui.table("complaints", model, Complaint::overdue);

    public ComplaintsPanel(Services services, Session session) {
        super(new BorderLayout(0, 8));
        this.services = services;
        this.session = session;
        Ui.pad(this, 16);

        status.setName("statusFilter");
        status.addItem(new Ui.Choice<>(null, "All statuses"));
        for (ComplaintStatus s : ComplaintStatus.values()) {
            status.addItem(new Ui.Choice<>(s, s.name()));
        }
        status.addActionListener(e -> refresh());
        overdueOnly.setName("overdueOnly");
        overdueOnly.addActionListener(e -> refresh());
        JButton assign = new JButton("Assign staff...");
        assign.setName("assignStaff");
        assign.addActionListener(e -> assign());
        JButton advance = new JButton("Advance status");
        advance.setName("advanceStatus");
        advance.addActionListener(e -> advance());
        JLabel legend = new JLabel("Red rows are overdue (BR-6).");
        legend.setForeground(Ui.MUTED);

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(new JLabel("Status"));
        bar.add(status);
        bar.add(overdueOnly);
        bar.add(assign);
        bar.add(advance);
        bar.add(legend);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void refresh() {
        ComplaintStatus s = ((Ui.Choice<ComplaintStatus>) status.getSelectedItem()).value();
        boolean overdue = overdueOnly.isSelected();
        Ui.async(this, () -> services.complaints().blockComplaints(session, s, overdue), model::setRows);
    }

    private Complaint selection() {
        Complaint c = Ui.selected(table, model);
        if (c == null) {
            Ui.error(this, "Select a complaint first.");
        }
        return c;
    }

    private void assign() {
        Complaint c = selection();
        if (c != null) {
            new AssignStaffDialog(SwingUtilities.getWindowAncestor(this), services, session, c, this::refresh)
                    .setVisible(true);
        }
    }

    /** Moves one step forward; the service enforces the BR-5 state machine. */
    private void advance() {
        Complaint c = selection();
        if (c == null) {
            return;
        }
        ComplaintStatus next = c.status().next();
        if (next != null && !Ui.confirm(this, "Move complaint #" + c.complaintId() + " from " + c.status()
                + " to " + next + "?")) {
            return;
        }
        Ui.async(this, () -> {
            services.complaints().updateStatus(session, c.complaintId(), next);
            return c;
        }, done -> refresh());
    }
}
