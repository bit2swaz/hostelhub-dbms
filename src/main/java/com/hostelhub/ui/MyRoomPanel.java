package com.hostelhub.ui;

import com.hostelhub.model.MyRoom;
import com.hostelhub.model.Student;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridBagLayout;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/** FR-06: current block, room, floor, type, start date and roommates; empty state if unallocated. */
public class MyRoomPanel extends JPanel implements Refreshable {

    private final Services services;
    private final Session session;
    private final CardLayout cards = new CardLayout();
    private final JLabel block = value("block");
    private final JLabel room = value("room");
    private final JLabel floor = value("floor");
    private final JLabel type = value("type");
    private final JLabel since = value("since");
    private final ListTableModel<Student> roommates = new ListTableModel<>(
            s -> new Object[] {s.fullName(), s.regNo(), s.programme(), s.yearOfStudy(), s.email()},
            "Name", "Reg No", "Programme", "Year", "Email");

    public MyRoomPanel(Services services, Session session) {
        this.services = services;
        this.session = session;
        setLayout(cards);

        JPanel details = Forms.grid("Block", block, "Room", room, "Floor", floor, "Room type", type,
                "Allocated since", since);
        details.setBorder(BorderFactory.createTitledBorder("Room details"));
        JScrollPane table = new JScrollPane(Ui.table("roommates", roommates));
        table.setBorder(BorderFactory.createTitledBorder("Roommates"));
        JPanel top = new JPanel(new BorderLayout());
        top.add(details, BorderLayout.WEST);
        JPanel roomCard = new JPanel(new BorderLayout(0, 12));
        Ui.pad(roomCard, 16);
        roomCard.add(top, BorderLayout.NORTH);
        roomCard.add(table, BorderLayout.CENTER);

        JPanel empty = new JPanel(new GridBagLayout());
        JPanel msg = new JPanel();
        msg.setLayout(new BoxLayout(msg, BoxLayout.Y_AXIS));
        JLabel h = Ui.heading("No room allocated yet");
        h.setName("emptyState");
        JLabel hint = new JLabel("Your block warden will allocate a room. You can raise complaints once you have one.");
        hint.setForeground(Ui.MUTED);
        msg.add(h);
        msg.add(hint);
        empty.add(msg);

        add(roomCard, "room");
        add(empty, "empty");
    }

    private static JLabel value(String name) {
        JLabel l = new JLabel("-");
        l.setName(name);
        l.setHorizontalAlignment(SwingConstants.LEFT);
        l.setFont(l.getFont().deriveFont(java.awt.Font.BOLD));
        return l;
    }

    @Override
    public void refresh() {
        Ui.async(this, () -> services.students().myRoom(session), this::show);
    }

    private void show(Optional<MyRoom> r) {
        if (r.isEmpty()) {
            cards.show(this, "empty");
            return;
        }
        MyRoom m = r.get();
        block.setText(m.blockName());
        room.setText(m.roomNo());
        floor.setText(m.floorNo() == 0 ? "Ground (0)" : String.valueOf(m.floorNo()));
        type.setText(m.roomType() + " (" + m.capacity() + (m.capacity() == 1 ? " bed)" : " beds)"));
        since.setText(Ui.date(m.startDate()));
        roommates.setRows(m.roommates());
        cards.show(this, "room");
    }
}
