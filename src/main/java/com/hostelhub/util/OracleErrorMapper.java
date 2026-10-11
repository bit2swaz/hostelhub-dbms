package com.hostelhub.util;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns Oracle errors into plain-English messages that name the rule or constraint. Never exposes stack traces. */
public final class OracleErrorMapper {

    private static final Pattern CONSTRAINT = Pattern.compile("\\(\\w+\\.(\\w+)\\)");
    private static final Pattern APP_ERROR = Pattern.compile("ORA-200\\d\\d: ([^\\n]+)");

    private static final Map<String, String> MESSAGES = Map.ofEntries(
            Map.entry("UX_ALLOC_ONE_ACTIVE", "This student already has an active allocation (BR-2)."),
            Map.entry("UQ_WARDEN_BLOCK", "That block already has a warden (BR-4, uq_warden_block)."),
            Map.entry("UQ_HOSTEL_USER_EMAIL", "That email address is already registered (uq_hostel_user_email)."),
            Map.entry("UQ_STUDENT_REG_NO", "That registration number is already registered (uq_student_reg_no)."),
            Map.entry("UQ_BLOCK_NAME", "That block name is already in use (uq_block_name)."),
            Map.entry("PK_ROOM", "That room number already exists in this block (pk_room)."),
            Map.entry("CK_ROOM_CAPACITY_TYPE", "Capacity must match the room type: SINGLE=1, DOUBLE=2, TRIPLE=3, QUAD=4 (ck_room_capacity_type)."),
            Map.entry("CK_HOSTEL_USER_EMAIL", "Enter a valid email address (ck_hostel_user_email)."),
            Map.entry("CK_HOSTEL_USER_PHONE", "Phone must be exactly 10 digits (ck_hostel_user_phone)."),
            Map.entry("CK_STAFF_PHONE", "Phone must be exactly 10 digits (ck_staff_phone)."),
            Map.entry("CK_STUDENT_REG_NO", "Registration number must look like 25BCE0231 (ck_student_reg_no)."),
            Map.entry("CK_STUDENT_YEAR_OF_STUDY", "Year of study must be between 1 and 5 (ck_student_year_of_study)."),
            Map.entry("CK_BLOCK_NUM_FLOORS", "Number of floors must be between 1 and 20 (ck_block_num_floors)."),
            Map.entry("CK_COMPLAINT_DESCRIPTION", "Description must be at least 10 characters (ck_complaint_description)."),
            Map.entry("CK_COMPLAINT_STATUS", "Status must be OPEN, IN_PROGRESS or RESOLVED (BR-5, ck_complaint_status)."),
            Map.entry("CK_COMPLAINT_DATES", "Resolved date cannot be before the raised date (BR-5, ck_complaint_dates)."),
            Map.entry("CK_COMPLAINT_RESOLVED", "Resolved date must be set exactly when a complaint is RESOLVED (BR-5, ck_complaint_resolved)."),
            Map.entry("CK_COMPLAINT_STAFF", "Assign staff before work can start on a complaint (ck_complaint_staff)."),
            Map.entry("FK_ALLOCATION_ROOM", "This room has allocation history and cannot be deleted (fk_allocation_room)."),
            Map.entry("FK_COMPLAINT_ROOM", "This room has complaint history and cannot be deleted (fk_complaint_room)."),
            Map.entry("FK_ALLOCATION_STUDENT", "This student has allocation history and cannot be deleted (fk_allocation_student)."),
            Map.entry("FK_COMPLAINT_STUDENT", "This student has complaint history and cannot be deleted (fk_complaint_student)."));

    private OracleErrorMapper() {
    }

    public static String toMessage(SQLException e) {
        String text = e.getMessage() == null ? "" : e.getMessage();
        int code = e.getErrorCode();
        if (code >= 20001 && code <= 20005) {
            Matcher m = APP_ERROR.matcher(text);
            return m.find() ? m.group(1).trim() : "The database rejected this change (ORA-" + code + ").";
        }
        String constraint = constraintName(text);
        if (constraint != null && MESSAGES.containsKey(constraint)) {
            return MESSAGES.get(constraint);
        }
        String name = constraint == null ? "" : " (" + constraint.toLowerCase(Locale.ROOT) + ")";
        return switch (code) {
            case 1 -> "That value is already in use" + name + ".";
            case 2290 -> "A value is not allowed" + name + ".";
            case 2291 -> "A referenced record does not exist" + name + ".";
            case 2292 -> "This record is still referenced by other records and cannot be deleted" + name + ".";
            case 1400, 1407 -> "A required field is missing.";
            case 54, 30006 -> "The record is busy. Please try again.";
            case 17002, 12541, 12514, 1017 -> "Cannot connect to the database. Check that Oracle is running and db.properties is correct.";
            default -> "Database error (ORA-" + String.format("%05d", code) + "). Please try again.";
        };
    }

    private static String constraintName(String text) {
        Matcher m = CONSTRAINT.matcher(text);
        return m.find() ? m.group(1).toUpperCase(Locale.ROOT) : null;
    }
}
