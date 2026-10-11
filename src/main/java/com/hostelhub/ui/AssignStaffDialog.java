package com.hostelhub.ui;

import com.hostelhub.model.Complaint;
import com.hostelhub.model.Staff;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JLabel;

/** FR-08: only staff of the complaint's category are offered. */
public class AssignStaffDialog extends FormDialog {

    private final Services services;
    private final Session session;
    private final Complaint complaint;
    private final Runnable onSaved;
    private final JComboBox<Ui.Choice<Staff>> staff = new JComboBox<>();

    public AssignStaffDialog(Window owner, Services services, Session session, Complaint complaint, Runnable onSaved) {
        super(owner, "Assign staff to complaint #" + complaint.complaintId(), "Assign");
        this.services = services;
        this.session = session;
        this.complaint = complaint;
        this.onSaved = onSaved;
        staff.setName("staff");
        build(Forms.grid("Complaint", new JLabel(complaint.roomNo() + ": " + complaint.description()),
                "Category", new JLabel(complaint.categoryName()), "Staff", staff));
        Ui.async(() -> services.complaints().eligibleStaff(session, complaint.complaintId()), list -> {
            list.forEach(s -> staff.addItem(new Ui.Choice<>(s, s.fullName() + "  (" + s.phone() + ")")));
            if (complaint.staffId() != null) {
                for (int i = 0; i < staff.getItemCount(); i++) {
                    if (staff.getItemAt(i).value().staffId() == complaint.staffId()) {
                        staff.setSelectedIndex(i);
                    }
                }
            }
        }, error::setText);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void submit() {
        var s = (Ui.Choice<Staff>) staff.getSelectedItem();
        if (s == null) {
            error.setText("Choose a staff member.");
            return;
        }
        save(() -> {
            services.complaints().assignStaff(session, complaint.complaintId(), s.value().staffId());
            return s;
        }, done -> onSaved.run());
    }
}
