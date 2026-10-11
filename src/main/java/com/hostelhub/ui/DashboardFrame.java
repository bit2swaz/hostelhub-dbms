package com.hostelhub.ui;

import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.WindowConstants;

/** Common dashboard: header with the user and a Logout button, and one tab per screen. */
public abstract class DashboardFrame extends JFrame {

    protected final Services services;
    protected final Session session;
    protected final JLabel userLabel = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();

    protected DashboardFrame(Services services, Session session, String title) {
        super(title);
        this.services = services;
        this.session = session;
        setName("dashboard");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        JButton logout = new JButton("Log out");
        logout.setName("logout");
        logout.addActionListener(e -> logout());
        userLabel.setName("user");
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.add(userLabel);
        right.add(logout);
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new java.awt.Color(0xDADCE0)),
                BorderFactory.createEmptyBorder(8, 14, 8, 10)));
        header.add(Ui.heading("HostelHub"), BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        tabs.setName("tabs");
        // Also fires when the first tab is added, which loads that tab exactly once.
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() instanceof Refreshable r) {
                r.refresh();
            }
        });
        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(tabs, BorderLayout.CENTER);
        setMinimumSize(new Dimension(1024, 640));
        setSize(1240, 760);
        setLocationRelativeTo(null);
    }

    protected void addTab(String title, JComponent panel) {
        panel.setName(title);
        tabs.addTab(title, panel);
    }

    private void logout() {
        Session.end();
        dispose();
        new LoginFrame(services).setVisible(true);
    }
}
