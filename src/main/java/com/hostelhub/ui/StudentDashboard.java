package com.hostelhub.ui;

import com.hostelhub.service.Services;
import com.hostelhub.util.Session;

/** Student screens: My Room (FR-06) and My Complaints (FR-07, FR-10). */
public class StudentDashboard extends DashboardFrame {

    public StudentDashboard(Services services, Session session) {
        super(services, session, "HostelHub - Student - " + session.fullName());
        userLabel.setText(session.fullName() + "  |  Student");
        addTab("My Room", new MyRoomPanel(services, session));
        addTab("My Complaints", new MyComplaintsPanel(services, session));
    }
}
