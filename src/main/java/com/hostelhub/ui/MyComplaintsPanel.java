package com.hostelhub.ui;

import com.hostelhub.model.Complaint;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** FR-10: own complaints with an overdue highlight; FR-07: raise a complaint. */
public class MyComplaintsPanel extends JPanel implements Refreshable {

    private final Services services;
    private final Session session;
    private final ListTableModel<Complaint> model = new ListTableModel<>(c -> new Object[] {
        c.complaintId(), c.categoryName(), c.description(), c.blockName() + " " + c.roomNo(), c.status().name(),
        c.staffName() == null ? "Not assigned" : c.staffName(), Ui.date(c.raisedOn()), Ui.date(c.resolvedOn()),
        c.overdue() ? "Yes (" + c.daysOverdue() + " d)" : ""},
            "#", "Category", "Description", "Room", "Status", "Staff", "Raised on", "Resolved on", "Overdue");

    public MyComplaintsPanel(Services services, Session session) {
        super(new BorderLayout(0, 8));
        this.services = services;
        this.session = session;
        Ui.pad(this, 16);

        JButton raise = new JButton("Raise complaint...");
        raise.setName("raise");
        raise.addActionListener(e -> new RaiseComplaintDialog(SwingUtilities.getWindowAncestor(this), services, session,
                this::refresh).setVisible(true));
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh());
        JLabel legend = new JLabel("Rows in red are overdue: open longer than the category's SLA (BR-6).");
        legend.setForeground(Ui.MUTED);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(raise);
        bar.add(refresh);
        bar.add(legend);

        var table = Ui.table("complaints", model, Complaint::overdue);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        Ui.async(this, () -> services.complaints().myComplaints(session), model::setRows);
    }
}
