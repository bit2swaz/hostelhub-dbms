package com.hostelhub.model;

import java.time.LocalDateTime;

/** A complaint with joined names. overdue/daysOverdue come from V_OVERDUE_COMPLAINTS (BR-6). */
public record Complaint(long complaintId, long studentId, String studentName, String regNo, long blockId,
        String blockName, String roomNo, long categoryId, String categoryName, int slaDays, Long staffId,
        String staffName, String description, ComplaintStatus status, LocalDateTime raisedOn,
        LocalDateTime resolvedOn, boolean overdue, int daysOverdue) {
}
