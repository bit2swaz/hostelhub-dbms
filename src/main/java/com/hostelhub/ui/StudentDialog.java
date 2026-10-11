package com.hostelhub.ui;

import com.hostelhub.model.Student;
import com.hostelhub.service.Services;
import com.hostelhub.service.Validators;
import com.hostelhub.util.Session;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/** Register (with an initial password) or edit a student. */
public class StudentDialog extends FormDialog {

    private static final String[] PROGRAMMES = {
        "B.Tech Computer Science and Engineering", "B.Tech Information Technology",
        "B.Tech Electronics and Communication Engineering", "B.Tech Electrical and Electronics Engineering",
        "B.Tech Mechanical Engineering"};

    private final Services services;
    private final Session session;
    private final Student existing;
    private final Runnable onSaved;
    private final JTextField fullName = field("fullName");
    private final JTextField email = field("email");
    private final JTextField phone = field("phone");
    private final JTextField regNo = field("regNo");
    private final JComboBox<Ui.Choice<String>> gender = new JComboBox<>();
    private final JComboBox<String> programme = new JComboBox<>(PROGRAMMES);
    private final JSpinner year = new JSpinner(new SpinnerNumberModel(1, 1, 5, 1));
    private final JPasswordField password = new JPasswordField(20);

    public StudentDialog(Window owner, Services services, Session session, Student existing, Runnable onSaved) {
        super(owner, existing == null ? "Register student" : "Edit student " + existing.regNo(), "Save");
        this.services = services;
        this.session = session;
        this.existing = existing;
        this.onSaved = onSaved;
        gender.setName("gender");
        gender.addItem(new Ui.Choice<>("M", "Male"));
        gender.addItem(new Ui.Choice<>("F", "Female"));
        programme.setName("programme");
        programme.setEditable(true);
        year.setName("year");
        password.setName("initialPassword");
        if (existing != null) {
            fullName.setText(existing.fullName());
            email.setText(existing.email());
            phone.setText(existing.phone() == null ? "" : existing.phone());
            regNo.setText(existing.regNo());
            gender.setSelectedIndex("M".equals(existing.gender()) ? 0 : 1);
            programme.setSelectedItem(existing.programme());
            year.setValue(existing.yearOfStudy());
        }
        Object[] rows = {"Full name", fullName, "Email", email, "Phone (10 digits, optional)", phone,
            "Reg no (e.g. 25BCE0231)", regNo, "Gender", gender, "Programme", programme, "Year of study", year};
        if (existing == null) {
            Object[] withPassword = java.util.Arrays.copyOf(rows, rows.length + 2);
            withPassword[rows.length] = "Initial password (min 8)";
            withPassword[rows.length + 1] = password;
            rows = withPassword;
        }
        build(Forms.grid(rows));
    }

    private static JTextField field(String name) {
        JTextField f = new JTextField(24);
        f.setName(name);
        return f;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void submit() {
        String g = ((Ui.Choice<String>) gender.getSelectedItem()).value();
        Student form = Student.form(fullName.getText(), email.getText(), phone.getText(), regNo.getText(), g,
                String.valueOf(programme.getSelectedItem()), (Integer) year.getValue());
        Student s = existing == null ? form : form.withUserId(existing.userId());
        String pass = new String(password.getPassword());
        if (!valid(() -> Validators.student(Validators.normalize(s)))
                || (existing == null && !valid(() -> Validators.password(pass)))) {
            return;
        }
        save(() -> existing == null ? services.students().create(session, s, pass) : services.students().update(session, s),
                saved -> onSaved.run());
    }
}
