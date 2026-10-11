package com.hostelhub.ui;

import com.hostelhub.service.ServiceException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A modal form: fields, a red inline message and OK/Cancel. Input is validated before anything
 * reaches the database; service errors are shown in the same inline label.
 */
public abstract class FormDialog extends JDialog {

    protected final JLabel error = Ui.errorLabel();
    protected final JButton ok;

    protected FormDialog(Window owner, String title, String okText) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setName("dialog");
        ok = new JButton(okText);
        ok.setName("ok");
        ok.addActionListener(e -> {
            error.setText(" ");
            submit();
        });
        getRootPane().setDefaultButton(ok);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }

    protected void build(JComponent form) {
        JButton cancel = new JButton("Cancel");
        cancel.setName("cancel");
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ok);
        buttons.add(cancel);
        JPanel south = new JPanel(new BorderLayout());
        error.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        south.add(error, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        JPanel root = new JPanel(new BorderLayout(0, 8));
        Ui.pad(root, 12);
        root.add(form, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        setContentPane(root);
        pack();
        setSize(Math.max(getWidth(), 520), getHeight());
        setLocationRelativeTo(getOwner());
    }

    /** Validates and saves. */
    protected abstract void submit();

    /** Runs a validation; shows its message inline and returns false if it fails. */
    protected boolean valid(Runnable validation) {
        try {
            validation.run();
            return true;
        } catch (ServiceException e) {
            error.setText(e.getMessage());
            return false;
        }
    }

    /** Saves in the background; closes on success, otherwise shows the message inline. */
    protected <T> void save(Callable<T> work, Consumer<T> onSaved) {
        ok.setEnabled(false);
        Ui.async(work, result -> {
            dispose();
            onSaved.accept(result);
        }, message -> {
            error.setText(message);
            ok.setEnabled(true);
        });
    }
}
