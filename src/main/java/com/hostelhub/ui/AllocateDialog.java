package com.hostelhub.ui;

import com.hostelhub.model.Room;
import com.hostelhub.model.Student;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JLabel;

/**
 * FR-04: pick an unallocated student of the block's gender (BR-2, BR-3) and a room with a free bed
 * (BR-1). The service re-checks everything under a room lock, since the lists may be stale.
 */
public class AllocateDialog extends FormDialog {

    private final Services services;
    private final Session session;
    private final Runnable onSaved;
    private final JComboBox<Ui.Choice<Student>> student = new JComboBox<>();
    private final JComboBox<Ui.Choice<Room>> room = new JComboBox<>();

    public AllocateDialog(Window owner, Services services, Session session, Runnable onSaved) {
        super(owner, "Allocate room", "Allocate");
        this.services = services;
        this.session = session;
        this.onSaved = onSaved;
        student.setName("student");
        room.setName("room");
        JLabel hint = new JLabel("Only unallocated students of this block's gender and rooms with free beds are listed.");
        hint.setForeground(Ui.MUTED);
        build(Forms.grid("Student", student, "Room", room, "", hint));
        Ui.async(() -> services.allocations().eligibleStudents(session), list -> {
            list.forEach(s -> student.addItem(new Ui.Choice<>(s, s.regNo() + "  " + s.fullName())));
            if (list.isEmpty()) {
                error.setText("No unallocated students of this block's gender.");
            }
        }, error::setText);
        Ui.async(() -> services.allocations().availableRooms(session), list -> {
            list.forEach(r -> room.addItem(new Ui.Choice<>(r, roomLabel(r))));
            if (list.isEmpty()) {
                error.setText("No rooms with free beds in this block.");
            }
        }, error::setText);
    }

    static String roomLabel(Room r) {
        return r.roomNo() + "  " + r.roomType() + ", floor " + r.floorNo() + ", " + r.vacancies() + " of "
                + r.capacity() + " free";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void submit() {
        var s = (Ui.Choice<Student>) student.getSelectedItem();
        var r = (Ui.Choice<Room>) room.getSelectedItem();
        if (s == null || r == null) {
            error.setText("Choose a student and a room.");
            return;
        }
        save(() -> services.allocations().allocate(session, s.value().userId(), r.value().roomNo()), id -> onSaved.run());
    }
}
