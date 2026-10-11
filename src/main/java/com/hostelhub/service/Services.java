package com.hostelhub.service;

import com.hostelhub.db.ConnectionFactory;

/** All services wired to one ConnectionFactory, handed to the UI. */
public record Services(AuthService auth, StudentService students, RoomService rooms,
        AllocationService allocations, ComplaintService complaints, ReportService reports) {

    public static Services create(ConnectionFactory db) {
        return new Services(new AuthService(db), new StudentService(db), new RoomService(db),
                new AllocationService(db), new ComplaintService(db), new ReportService(db));
    }
}
