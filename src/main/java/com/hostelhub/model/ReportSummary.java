package com.hostelhub.model;

/** FR-11 summary counts for a warden's block. */
public record ReportSummary(int rooms, int beds, int occupied, int vacant, int openComplaints,
        int overdueComplaints) {
}
