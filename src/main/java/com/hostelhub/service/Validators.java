package com.hostelhub.service;

import com.hostelhub.model.Role;
import com.hostelhub.model.Student;
import com.hostelhub.util.Session;
import java.util.regex.Pattern;

/**
 * Input and access checks that run before anything reaches the database.
 * The UI calls the same methods to show inline messages; services call them again as the authority.
 */
public final class Validators {

    // Same patterns as the CHECK constraints in sql/01_schema.sql.
    static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    static final Pattern PHONE = Pattern.compile("^[0-9]{10}$");
    static final Pattern REG_NO = Pattern.compile("^[0-9]{2}[A-Z]{3}[0-9]{4}$");
    static final Pattern ROOM_NO = Pattern.compile("^[A-Za-z0-9-]{1,6}$");

    public static final int MIN_PASSWORD = 8;
    public static final int MIN_DESCRIPTION = 10;

    private Validators() {
    }

    /** BR-4: the caller must be a warden; returns the only block they may act on. */
    public static long wardenBlock(Session s) {
        if (s == null || s.role() != Role.WARDEN || s.blockId() == null) {
            throw new ServiceException("Only wardens can do this.");
        }
        return s.blockId();
    }

    public static long studentId(Session s) {
        if (s == null || s.role() != Role.STUDENT) {
            throw new ServiceException("Only students can do this.");
        }
        return s.userId();
    }

    /** Trims, upper-cases reg_no and gender, lower-cases email, and turns a blank phone into null. */
    public static Student normalize(Student s) {
        String phone = s.phone() == null || s.phone().isBlank() ? null : s.phone().trim();
        return new Student(s.userId(), trim(s.fullName()), trim(s.email()).toLowerCase(), phone,
                trim(s.regNo()).toUpperCase(), trim(s.gender()).toUpperCase(), trim(s.programme()),
                s.yearOfStudy(), s.blockName(), s.roomNo());
    }

    /** Checks a normalized student. */
    public static void student(Student s) {
        require(!s.fullName().isEmpty(), "Full name is required.");
        require(s.fullName().length() <= 100, "Full name must be at most 100 characters.");
        require(s.email().length() <= 120 && EMAIL.matcher(s.email()).matches(), "Enter a valid email address.");
        require(s.phone() == null || PHONE.matcher(s.phone()).matches(), "Phone must be exactly 10 digits.");
        require(REG_NO.matcher(s.regNo()).matches(), "Registration number must look like 25BCE0231.");
        require(s.gender().equals("M") || s.gender().equals("F"), "Gender must be M or F.");
        require(!s.programme().isEmpty(), "Programme is required.");
        require(s.programme().length() <= 60, "Programme must be at most 60 characters.");
        require(s.yearOfStudy() >= 1 && s.yearOfStudy() <= 5, "Year of study must be between 1 and 5.");
    }

    public static void password(String password) {
        require(password != null && password.length() >= MIN_PASSWORD,
                "Initial password must be at least " + MIN_PASSWORD + " characters.");
    }

    public static void block(String name, String genderType, int numFloors) {
        require(name != null && !name.isBlank(), "Block name is required.");
        require(name.trim().length() <= 30, "Block name must be at most 30 characters.");
        require("M".equals(genderType) || "F".equals(genderType), "Gender type must be M or F.");
        require(numFloors >= 1 && numFloors <= 20, "Number of floors must be between 1 and 20.");
    }

    public static void room(String roomNo, int floorNo, int numFloors) {
        require(roomNo != null && ROOM_NO.matcher(roomNo.trim()).matches(),
                "Room number must be 1-6 letters, digits or '-'.");
        require(floorNo >= 0 && floorNo < numFloors,
                "Floor must be between 0 and " + (numFloors - 1) + " for this block.");
    }

    public static String description(String description) {
        String d = description == null ? "" : description.trim();
        require(d.length() >= MIN_DESCRIPTION, "Description must be at least " + MIN_DESCRIPTION + " characters.");
        require(d.length() <= 500, "Description must be at most 500 characters.");
        return d;
    }

    static void require(boolean ok, String message) {
        if (!ok) {
            throw new ServiceException(message);
        }
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
