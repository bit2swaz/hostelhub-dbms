package com.hostelhub.model;

import java.time.LocalDate;

public record Allocation(long allocId, long studentId, String studentName, String regNo, long blockId,
        String roomNo, String approvedByName, LocalDate startDate, LocalDate endDate, String status) {

    public static final String ACTIVE = "ACTIVE";
    public static final String VACATED = "VACATED";

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
