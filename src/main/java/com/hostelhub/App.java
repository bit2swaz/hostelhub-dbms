package com.hostelhub;

import com.formdev.flatlaf.FlatLightLaf;
import com.hostelhub.config.DbConfig;
import com.hostelhub.db.ConnectionFactory;
import com.hostelhub.service.Services;
import com.hostelhub.ui.LoginFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** HostelHub entry point: Swing UI -> services -> DAOs -> ojdbc11 -> Oracle XE. */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Services services = Services.create(new ConnectionFactory(DbConfig.load()));
        SwingUtilities.invokeLater(() -> {
            installLookAndFeel();
            new LoginFrame(services).setVisible(true);
        });
    }

    public static void installLookAndFeel() {
        FlatLightLaf.setup();
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("Table.intercellSpacing", new java.awt.Dimension(0, 1));
        UIManager.put("TabbedPane.showTabSeparators", true);
    }
}
