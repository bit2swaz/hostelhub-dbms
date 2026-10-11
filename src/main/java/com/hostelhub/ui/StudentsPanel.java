package com.hostelhub.ui;

import com.hostelhub.model.Student;
import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** FR-03: searchable student table with add/edit/delete. */
public class StudentsPanel extends JPanel implements Refreshable {

    private final Services services;
    private final Session session;
    private final JTextField search = new JTextField(22);
    private final JLabel count = new JLabel();
    private final ListTableModel<Student> model = new ListTableModel<>(s -> new Object[] {
        s.regNo(), s.fullName(), Ui.gender(s.gender()), s.programme(), s.yearOfStudy(), s.email(),
        s.phone() == null ? "" : s.phone(), s.roomNo() == null ? "Not allocated" : s.blockName() + " " + s.roomNo()},
            "Reg No", "Name", "Gender", "Programme", "Year", "Email", "Phone", "Current room");
    private final JTable table = Ui.table("students", model);

    public StudentsPanel(Services services, Session session) {
        super(new BorderLayout(0, 8));
        this.services = services;
        this.session = session;
        Ui.pad(this, 16);

        search.setName("search");
        search.addActionListener(e -> refresh());
        JButton find = new JButton("Search");
        find.setName("searchButton");
        find.addActionListener(e -> refresh());
        JButton add = new JButton("Add student...");
        add.setName("addStudent");
        add.addActionListener(e -> open(null));
        JButton edit = new JButton("Edit...");
        edit.setName("editStudent");
        edit.addActionListener(e -> {
            Student s = Ui.selected(table, model);
            if (s == null) {
                Ui.error(this, "Select a student to edit.");
            } else {
                open(s);
            }
        });
        JButton delete = new JButton("Delete");
        delete.setName("deleteStudent");
        delete.addActionListener(e -> delete());
        count.setForeground(Ui.MUTED);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(new JLabel("Reg no or name"));
        bar.add(search);
        bar.add(find);
        bar.add(add);
        bar.add(edit);
        bar.add(delete);
        bar.add(count);
        add(bar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        String text = search.getText();
        Ui.async(this, () -> services.students().search(session, text), rows -> {
            model.setRows(rows);
            count.setText(rows.size() + (rows.size() == 1 ? " student" : " students"));
        });
    }

    private void open(Student s) {
        new StudentDialog(SwingUtilities.getWindowAncestor(this), services, session, s, this::refresh).setVisible(true);
    }

    private void delete() {
        Student s = Ui.selected(table, model);
        if (s == null) {
            Ui.error(this, "Select a student to delete.");
            return;
        }
        if (!Ui.confirm(this, "Delete student " + s.regNo() + " (" + s.fullName() + ")? This cannot be undone.")) {
            return;
        }
        Ui.async(this, () -> {
            services.students().delete(session, s.userId());
            return s;
        }, deleted -> refresh());
    }
}
