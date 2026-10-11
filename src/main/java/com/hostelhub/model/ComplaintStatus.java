package com.hostelhub.model;

/** BR-5: complaints only move forward, OPEN -> IN_PROGRESS -> RESOLVED. */
public enum ComplaintStatus {
    OPEN, IN_PROGRESS, RESOLVED;

    /** The only allowed next status, or null when RESOLVED. */
    public ComplaintStatus next() {
        return this == RESOLVED ? null : values()[ordinal() + 1];
    }
}
