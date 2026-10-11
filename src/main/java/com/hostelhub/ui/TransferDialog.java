package com.hostelhub.ui;

import com.hostelhub.model.Allocation;
import com.hostelhub.model.Room;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JLabel;

/** FR-05: move a student to another room in the same block, as one transaction. */
public class TransferDialog extends FormDialog {

    private final Services services;
    private final Session session;
    private final Allocation allocation;
    private final Runnable onSaved;
    private final JComboBox<Ui.Choice<Room>> room = new JComboBox<>();

    public TransferDialog(Window owner, Services services, Session session, Allocation allocation, Runnable onSaved) {
        super(owner, "Transfer " + allocation.studentName(), "Transfer");
        this.services = services;
        this.session = session;
        this.allocation = allocation;
        this.onSaved = onSaved;
        room.setName("room");
        build(Forms.grid("Student", new JLabel(allocation.regNo() + "  " + allocation.studentName()),
                "Current room", new JLabel(allocation.roomNo()), "New room", room));
        Ui.async(() -> services.allocations().availableRooms(session), list -> list.stream()
                .filter(r -> !r.roomNo().equals(allocation.roomNo()))
                .forEach(r -> room.addItem(new Ui.Choice<>(r, AllocateDialog.roomLabel(r)))), error::setText);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void submit() {
        var r = (Ui.Choice<Room>) room.getSelectedItem();
        if (r == null) {
            error.setText("Choose the new room.");
            return;
        }
        save(() -> services.allocations().transfer(session, allocation.allocId(), r.value().roomNo()),
                id -> onSaved.run());
    }
}
