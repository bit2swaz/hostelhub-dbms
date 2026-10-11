package com.hostelhub.ui;

import com.hostelhub.model.Category;
import com.hostelhub.service.Services;
import com.hostelhub.service.Validators;
import com.hostelhub.util.Session;
import java.awt.Window;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** FR-07: category + description; the room is filled in from the student's active allocation. */
public class RaiseComplaintDialog extends FormDialog {

    private final Services services;
    private final Session session;
    private final Runnable onSaved;
    private final JComboBox<Ui.Choice<Category>> category = new JComboBox<>();
    private final JTextArea description = new JTextArea(5, 36);
    private final JLabel room = new JLabel("Loading...");
    private final JLabel counter = new JLabel("0 / 500");

    public RaiseComplaintDialog(Window owner, Services services, Session session, Runnable onSaved) {
        super(owner, "Raise complaint", "Submit");
        this.services = services;
        this.session = session;
        this.onSaved = onSaved;
        category.setName("category");
        description.setName("description");
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        room.setName("room");
        counter.setForeground(Ui.MUTED);
        description.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                count();
            }

            public void removeUpdate(DocumentEvent e) {
                count();
            }

            public void changedUpdate(DocumentEvent e) {
                count();
            }
        });
        build(Forms.grid("Room", room, "Category", category, "Description", new JScrollPane(description),
                "", counter));
        ok.setEnabled(false);
        Ui.async(() -> services.complaints().categories(), cats -> {
            cats.forEach(c -> category.addItem(new Ui.Choice<>(c, c.categoryName() + "  (SLA " + c.slaDays()
                    + (c.slaDays() == 1 ? " day)" : " days)"))));
        }, error::setText);
        Ui.async(() -> services.students().myRoom(session), r -> {
            if (r.isEmpty()) {
                room.setText("No active allocation");
                error.setText("You need an active room allocation to raise a complaint.");
            } else {
                room.setText(r.get().roomNo() + " (" + r.get().blockName() + ")");
                ok.setEnabled(true);
            }
        }, error::setText);
    }

    private void count() {
        counter.setText(description.getText().trim().length() + " / 500");
    }

    @Override
    protected void submit() {
        @SuppressWarnings("unchecked")
        Ui.Choice<Category> c = (Ui.Choice<Category>) category.getSelectedItem();
        if (c == null) {
            error.setText("Choose a category.");
            return;
        }
        if (!valid(() -> Validators.description(description.getText()))) {
            return;
        }
        save(() -> services.complaints().raise(session, c.value().categoryId(), description.getText()),
                id -> onSaved.run());
    }
}
