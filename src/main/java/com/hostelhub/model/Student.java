package com.hostelhub.model;

/** A student (HOSTEL_USER + STUDENT). blockName/roomNo are the current ACTIVE room, or null. */
public record Student(long userId, String fullName, String email, String phone, String regNo,
        String gender, String programme, int yearOfStudy, String blockName, String roomNo) {

    /** A new or edited student as entered in a form (no id, no room yet). */
    public static Student form(String fullName, String email, String phone, String regNo,
            String gender, String programme, int yearOfStudy) {
        return new Student(0, fullName, email, phone, regNo, gender, programme, yearOfStudy, null, null);
    }

    public Student withUserId(long id) {
        return new Student(id, fullName, email, phone, regNo, gender, programme, yearOfStudy, blockName, roomNo);
    }
}
