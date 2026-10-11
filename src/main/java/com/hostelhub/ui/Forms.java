package com.hostelhub.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Two-column label/field grids. */
public final class Forms {

    private Forms() {
    }

    /** Arguments alternate: label text, component, label text, component... */
    public static JPanel grid(Object... labelsAndFields) {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 8);
        for (int i = 0; i < labelsAndFields.length; i += 2) {
            c.gridy = i / 2;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.anchor = GridBagConstraints.LINE_START;
            p.add(new JLabel((String) labelsAndFields[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add((JComponent) labelsAndFields[i + 1], c);
        }
        return p;
    }
}
