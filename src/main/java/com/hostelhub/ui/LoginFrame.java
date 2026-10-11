package com.hostelhub.ui;

import com.hostelhub.service.Services;
import com.hostelhub.util.Session;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.WindowConstants;

/** FR-01: email + password; Enter submits; routes to the Student or Warden dashboard. */
public class LoginFrame extends JFrame {

    private final Services services;
    private final JTextField email = new JTextField(24);
    private final JPasswordField password = new JPasswordField(24);
    private final JButton login = new JButton("Log in");
    private final JLabel error = Ui.errorLabel();

    public LoginFrame(Services services) {
        super("HostelHub - Login");
        this.services = services;
        setName("loginFrame");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        email.setName("email");
        password.setName("password");
        login.setName("login");
        login.addActionListener(e -> doLogin());
        getRootPane().setDefaultButton(login);

        JLabel title = Ui.heading("HostelHub");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));
        JLabel subtitle = new JLabel("Hostel Room Allocation & Complaint Management");
        subtitle.setForeground(Ui.MUTED);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(0xDADCE0)),
                BorderFactory.createEmptyBorder(24, 28, 24, 28)));
        for (var c : new javax.swing.JComponent[] {title, subtitle}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            card.add(c);
        }
        card.add(Box.createVerticalStrut(18));
        JPanel form = Forms.grid("Email", email, "Password", password);
        form.setAlignmentX(LEFT_ALIGNMENT);
        card.add(form);
        card.add(Box.createVerticalStrut(6));
        error.setAlignmentX(LEFT_ALIGNMENT);
        card.add(error);
        card.add(Box.createVerticalStrut(10));
        login.setAlignmentX(LEFT_ALIGNMENT);
        card.add(login);

        JPanel center = new JPanel(new GridBagLayout());
        center.add(card);
        getContentPane().add(center, BorderLayout.CENTER);
        setSize(560, 420);
        setLocationRelativeTo(null);
    }

    private void doLogin() {
        String mail = email.getText();
        String pass = new String(password.getPassword());
        if (mail.isBlank() || pass.isEmpty()) {
            error.setText("Enter your email and password.");
            return;
        }
        error.setText(" ");
        login.setEnabled(false);
        Ui.async(() -> services.auth().login(mail, pass), session -> {
            Session.start(session);
            dispose();
            (session.isWarden() ? new WardenDashboard(services, session) : new StudentDashboard(services, session))
                    .setVisible(true);
        }, message -> {
            error.setText(message);
            password.setText("");
            login.setEnabled(true);
        });
    }
}
