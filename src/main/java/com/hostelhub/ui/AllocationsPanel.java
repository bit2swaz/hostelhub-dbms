package com.hostelhub.ui;

import com.hostelhub.model.Allocation;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/** FR-04 allocate, FR-05 vacate/transfer, and the block's allocation history with a status filter. */
public class AllocationsPanel extends JPanel implements Refreshable {

    private final Services services;
    private final Session session;
    private final JComboBox<Ui.Choice<String>> filter = new JComboBox<>();
    private final ListTableModel<Allocation> model = new ListTableModel<>(a -> new Object[] {
        a.allocId(), a.regNo(), a.studentName(), a.roomNo(), Ui.date(a.startDate()), Ui.date(a.endDate()),
        a.status(), a.approvedByName()},
            "#", "Reg No", "Student", "Room", "Start date", "End date", "Status", "Approved by");
    private final JTable table = Ui.table("allocations", model);

    public AllocationsPanel(Services services, Session session) {
        super(new BorderLayout(0, 8));
        this.services = services;
        this.session = session;
        Ui.pad(this, 16);

        JButton allocate = new JButton("Allocate room...");
        allocate.setName("allocate");
        allocate.addActionListener(e -> new AllocateDialog(SwingUtilities.getWindowAncestor(this), services, session,
                this::refresh).setVisible(true));
        JButton vacate = new JButton("Vacate");
        vacate.setName("vacate");
        vacate.addActionListener(e -> vacate());
        JButton transfer = new JButton("Transfer...");
        transfer.setName("transfer");
        transfer.addActionListener(e -> {
            Allocation a = activeSelection();
            if (a != null) {
                new TransferDialog(SwingUtilities.getWindowAncestor(this), services, session, a, this::refresh)
                        .setVisible(true);
            }
        });
        filter.setName("statusFilter");
        filter.addItem(new Ui.Choice<>(null, "All"));
        filter.addItem(new Ui.Choice<>("ACTIVE", "Active"));
        filter.addItem(new Ui.Choice<>("VACATED", "Vacated"));
        filter.addActionListener(e -> refresh());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(allocate);
        bar.add(vacate);
        bar.add(transfer);
        bar.add(new JLabel("    Show"));
        bar.add(filter);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void refresh() {
        String status = ((Ui.Choice<String>) filter.getSelectedItem()).value();
        Ui.async(this, () -> services.allocations().history(session, status), model::setRows);
    }

    private Allocation activeSelection() {
        Allocation a = Ui.selected(table, model);
        if (a == null || !a.isActive()) {
            Ui.error(this, "Select an ACTIVE allocation first.");
            return null;
        }
        return a;
    }

    private void vacate() {
        Allocation a = activeSelection();
        if (a == null || !Ui.confirm(this, "Vacate " + a.studentName() + " (" + a.regNo() + ") from room "
                + a.roomNo() + "? The allocation is kept as history.")) {
            return;
        }
        Ui.async(this, () -> {
            services.allocations().vacate(session, a.allocId());
            return a;
        }, done -> refresh());
    }
}
