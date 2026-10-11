package com.hostelhub.ui;

import com.hostelhub.model.Block;
import com.hostelhub.model.Room;
import com.hostelhub.service.Services;
import com.hostelhub.service.Validators;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;

/** FR-02: edit own block details; rooms table with live occupancy; add/edit/delete rooms. */
public class BlockRoomsPanel extends JPanel implements Refreshable {

    private final Services services;
    private final Session session;
    private final JTextField name = new JTextField(18);
    private final JComboBox<Ui.Choice<String>> gender = new JComboBox<>();
    private final JSpinner floors = new JSpinner(new SpinnerNumberModel(1, 1, 20, 1));
    private final JLabel blockError = Ui.errorLabel();
    private final ListTableModel<Room> model = new ListTableModel<>(r -> new Object[] {
        r.roomNo(), r.floorNo(), r.roomType().name(), r.capacity(), r.occupancy(), r.vacancies()},
            "Room", "Floor", "Type", "Capacity", "Occupancy", "Vacancies");
    private final JTable table = Ui.table("rooms", model);
    private Block block;

    public BlockRoomsPanel(Services services, Session session) {
        super(new BorderLayout(0, 12));
        this.services = services;
        this.session = session;
        Ui.pad(this, 16);

        name.setName("blockName");
        gender.setName("blockGender");
        gender.addItem(new Ui.Choice<>("M", "Male"));
        gender.addItem(new Ui.Choice<>("F", "Female"));
        floors.setName("blockFloors");
        blockError.setName("blockError");
        JButton save = new JButton("Save block");
        save.setName("saveBlock");
        save.addActionListener(e -> saveBlock());
        JPanel blockForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        blockForm.add(new JLabel("Name"));
        blockForm.add(name);
        blockForm.add(new JLabel("Gender"));
        blockForm.add(gender);
        blockForm.add(new JLabel("Floors"));
        blockForm.add(floors);
        blockForm.add(save);
        blockForm.add(blockError);
        blockForm.setBorder(BorderFactory.createTitledBorder("Block details"));

        JButton add = button("Add room...", "addRoom");
        add.addActionListener(e -> openRoomDialog(null));
        JButton edit = button("Edit room...", "editRoom");
        edit.addActionListener(e -> {
            Room r = Ui.selected(table, model);
            if (r == null) {
                Ui.error(this, "Select a room to edit.");
            } else {
                openRoomDialog(r);
            }
        });
        JButton delete = button("Delete room", "deleteRoom");
        delete.addActionListener(e -> deleteRoom());
        JLabel hint = new JLabel("Occupancy is live from V_ROOM_OCCUPANCY.");
        hint.setForeground(Ui.MUTED);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(add);
        bar.add(edit);
        bar.add(delete);
        bar.add(hint);

        JPanel rooms = new JPanel(new BorderLayout(0, 8));
        rooms.setBorder(BorderFactory.createTitledBorder("Rooms"));
        rooms.add(bar, BorderLayout.NORTH);
        rooms.add(new JScrollPane(table), BorderLayout.CENTER);

        add(blockForm, BorderLayout.NORTH);
        add(rooms, BorderLayout.CENTER);
    }

    private static JButton button(String text, String name) {
        JButton b = new JButton(text);
        b.setName(name);
        return b;
    }

    @Override
    public void refresh() {
        Ui.async(this, () -> services.rooms().block(session), b -> {
            block = b;
            name.setText(b.blockName());
            gender.setSelectedIndex("M".equals(b.genderType()) ? 0 : 1);
            floors.setValue(b.numFloors());
        });
        Ui.async(this, () -> services.rooms().rooms(session), model::setRows);
    }

    @SuppressWarnings("unchecked")
    private void saveBlock() {
        String g = ((Ui.Choice<String>) gender.getSelectedItem()).value();
        int f = (Integer) floors.getValue();
        try {
            Validators.block(name.getText(), g, f);
        } catch (com.hostelhub.service.ServiceException ex) {
            blockError.setText(ex.getMessage());
            return;
        }
        blockError.setText(" ");
        Ui.async(() -> services.rooms().updateBlock(session, name.getText(), g, f), b -> {
            blockError.setText("Saved.");
            refresh();
        }, blockError::setText);
    }

    private void openRoomDialog(Room room) {
        if (block == null) {
            return;
        }
        new RoomDialog(SwingUtilities.getWindowAncestor(this), services, session, block, room, this::refresh)
                .setVisible(true);
    }

    private void deleteRoom() {
        Room r = Ui.selected(table, model);
        if (r == null) {
            Ui.error(this, "Select a room to delete.");
            return;
        }
        if (!Ui.confirm(this, "Delete room " + r.roomNo() + "? This cannot be undone.")) {
            return;
        }
        Ui.async(this, () -> {
            services.rooms().deleteRoom(session, r.roomNo());
            return r;
        }, deleted -> refresh());
    }
}
