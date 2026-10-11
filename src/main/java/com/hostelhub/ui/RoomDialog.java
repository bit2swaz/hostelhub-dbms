package com.hostelhub.ui;

import com.hostelhub.model.Block;
import com.hostelhub.model.Room;
import com.hostelhub.model.RoomType;
import com.hostelhub.service.Services;
import com.hostelhub.service.Validators;
import com.hostelhub.util.Session;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Add or edit a room. Capacity follows the type (SINGLE=1 ... QUAD=4). */
public class RoomDialog extends FormDialog {

    private final Services services;
    private final Session session;
    private final Room existing;
    private final Runnable onSaved;
    private final Block block;
    private final JTextField roomNo = new JTextField(8);
    private final JSpinner floor;
    private final JComboBox<RoomType> type = new JComboBox<>(RoomType.values());
    private final JLabel capacity = new JLabel();

    public RoomDialog(Window owner, Services services, Session session, Block block, Room existing, Runnable onSaved) {
        super(owner, existing == null ? "Add room" : "Edit room " + existing.roomNo(), "Save");
        this.services = services;
        this.session = session;
        this.block = block;
        this.existing = existing;
        this.onSaved = onSaved;
        floor = new JSpinner(new SpinnerNumberModel(0, 0, Math.max(0, block.numFloors() - 1), 1));
        roomNo.setName("roomNo");
        floor.setName("floor");
        type.setName("type");
        capacity.setName("capacity");
        type.addActionListener(e -> capacity.setText(beds((RoomType) type.getSelectedItem())));
        if (existing != null) {
            roomNo.setText(existing.roomNo());
            roomNo.setEnabled(false);
            floor.setValue(existing.floorNo());
            type.setSelectedItem(existing.roomType());
        }
        capacity.setText(beds((RoomType) type.getSelectedItem()));
        build(Forms.grid("Block", new JLabel(block.blockName()), "Room number", roomNo,
                "Floor (0 = ground)", floor, "Type", type, "Capacity", capacity));
    }

    private static String beds(RoomType t) {
        return t.capacity() + (t.capacity() == 1 ? " bed" : " beds");
    }

    @Override
    protected void submit() {
        String no = roomNo.getText().trim();
        int f = (Integer) floor.getValue();
        RoomType t = (RoomType) type.getSelectedItem();
        if (!valid(() -> Validators.room(no, f, block.numFloors()))) {
            return;
        }
        save(() -> {
            if (existing == null) {
                services.rooms().addRoom(session, no, f, t);
            } else {
                services.rooms().updateRoom(session, existing.roomNo(), f, t);
            }
            return no;
        }, saved -> onSaved.run());
    }
}
