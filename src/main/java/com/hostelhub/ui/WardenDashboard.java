package com.hostelhub.ui;

import com.hostelhub.service.Services;
import com.hostelhub.util.Session;

/** Warden screens, all scoped to the warden's own block (BR-4). */
public class WardenDashboard extends DashboardFrame {

    public WardenDashboard(Services services, Session session) {
        super(services, session, "HostelHub - Warden - " + session.fullName());
        userLabel.setText(session.fullName() + "  |  Warden");
        Ui.async(this, () -> services.rooms().block(session), b -> {
            setTitle("HostelHub - Warden - " + b.blockName());
            userLabel.setText(session.fullName() + "  |  Warden, " + b.blockName());
        });
        addTab("Block & Rooms", new BlockRoomsPanel(services, session));
        addTab("Students", new StudentsPanel(services, session));
        addTab("Allocations", new AllocationsPanel(services, session));
        addTab("Complaints", new ComplaintsPanel(services, session));
        addTab("Reports", new ReportsPanel(services, session));
    }
}
