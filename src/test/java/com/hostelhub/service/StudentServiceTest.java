package com.hostelhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.model.MyRoom;
import com.hostelhub.model.RoomType;
import com.hostelhub.model.Student;
import com.hostelhub.support.ServiceTestBase;
import com.hostelhub.util.PasswordHasher;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** FR-03 (student records) and FR-06 (my room). */
class StudentServiceTest extends ServiceTestBase {

    private static Student newStudent() {
        return Student.form(" Rhea Kapoor ", "Rhea.Kapoor@HostelHub.test", "9876543210", "25bce2001", "f",
                "B.Tech Computer Science and Engineering", 1);
    }

    @Test
    void fr03_createInsertsUserAndStudentWithHashedPassword() throws Exception {
        Student created = students.create(wardenB(), newStudent(), "Welcome@2025");
        assertThat(created.userId()).isPositive();
        assertThat(created.regNo()).isEqualTo("25BCE2001");
        assertThat(created.email()).isEqualTo("rhea.kapoor@hostelhub.test");
        assertThat(created.fullName()).isEqualTo("Rhea Kapoor");
        assertThat(created.roomNo()).isNull();
        try (var c = connect(); var ps = c.prepareStatement("SELECT password FROM hostel_user WHERE user_id = ?")) {
            ps.setLong(1, created.userId());
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getString(1)).startsWith("pbkdf2$").doesNotContain("Welcome@2025");
                assertThat(PasswordHasher.verify("Welcome@2025", rs.getString(1))).isTrue();
            }
        }
        assertThat(auth.login("rhea.kapoor@hostelhub.test", "Welcome@2025").userId()).isEqualTo(created.userId());
    }

    @Test
    void fr03_createRejectsDuplicatesAndBadInputBeforeTheDatabase() {
        Student dupEmail = Student.form("X Y", "arjun.nair@hostelhub.test", null, "25BCE2001", "M", "B.Tech", 1);
        assertServiceRejects(() -> students.create(wardenA(), dupEmail, "Welcome@2025"),
                "That email address is already registered.");
        Student dupReg = Student.form("X Y", "x.y@hostelhub.test", null, "25BCE1045", "M", "B.Tech", 1);
        assertServiceRejects(() -> students.create(wardenA(), dupReg, "Welcome@2025"),
                "That registration number is already registered.");
        Student badReg = Student.form("X Y", "x.y@hostelhub.test", null, "BCE1045", "M", "B.Tech", 1);
        assertServiceRejects(() -> students.create(wardenA(), badReg, "Welcome@2025"),
                "Registration number must look like 25BCE0231.");
        Student badPhone = Student.form("X Y", "x.y@hostelhub.test", "12345", "25BCE2001", "M", "B.Tech", 1);
        assertServiceRejects(() -> students.create(wardenA(), badPhone, "Welcome@2025"),
                "Phone must be exactly 10 digits.");
        Student badYear = Student.form("X Y", "x.y@hostelhub.test", null, "25BCE2001", "M", "B.Tech", 6);
        assertServiceRejects(() -> students.create(wardenA(), badYear, "Welcome@2025"),
                "Year of study must be between 1 and 5.");
        assertServiceRejects(() -> students.create(wardenA(), newStudent(), "short"),
                "Initial password must be at least 8 characters.");
    }

    @Test
    void fr03_searchByRegNoOrNameIsCaseInsensitiveAndShowsCurrentRoom() {
        assertThat(students.search(wardenA(), "25bce1045")).singleElement()
                .satisfies(s -> {
                    assertThat(s.fullName()).isEqualTo("Arjun Nair");
                    assertThat(s.blockName()).isEqualTo("A Block");
                    assertThat(s.roomNo()).isEqualTo("A-103");
                });
        assertThat(students.search(wardenA(), "sharma")).extracting(Student::fullName).containsExactly("Priya Sharma");
        assertThat(students.search(wardenA(), "")).hasSize(45);
    }

    @Test
    void fr03_updateChangesUserAndStudentFields() throws Exception {
        long id = studentId("25BIT0633");
        Student edited = Student.form("Naveen K", "naveen.k@hostelhub.test", "9000000001", "25BIT0634", "M",
                "B.Tech Information Technology", 2).withUserId(id);
        Student saved = students.update(wardenA(), edited);
        assertThat(saved).extracting(Student::fullName, Student::email, Student::regNo, Student::yearOfStudy)
                .containsExactly("Naveen K", "naveen.k@hostelhub.test", "25BIT0634", 2);
    }

    @Test
    void br3_genderCannotChangeWhileAllocated() throws Exception {
        long id = studentId("25BCE1045");
        Student edited = Student.form("Arjun Nair", "arjun.nair@hostelhub.test", null, "25BCE1045", "F",
                "B.Tech Computer Science and Engineering", 1).withUserId(id);
        assertServiceRejects(() -> students.update(wardenA(), edited),
                "Cannot change gender while the student has an active room allocation (BR-3).");
    }

    @Test
    void fr03_deleteWithoutHistorySucceedsAndCascadesUser() throws Exception {
        students.delete(wardenA(), studentId("25BIT0633"));
        assertThat(queryLong("SELECT COUNT(*) FROM hostel_user WHERE email = 'naveen.kumar@hostelhub.test'")).isZero();
    }

    @Test
    void fr03_deleteWithHistoryIsBlocked() throws Exception {
        assertServiceRejects(() -> students.delete(wardenA(), studentId("24BME0518")),
                "Student 24BME0518 has allocation or complaint history and cannot be deleted.");
    }

    @Test
    void fr03_onlyWardensManageStudents() {
        assertServiceRejects(() -> students.search(login("arjun.nair@hostelhub.test"), ""), "Only wardens can do this.");
    }

    @Test
    void fr06_myRoomShowsRoomAndRoommates() throws Exception {
        MyRoom r = students.myRoom(login("arjun.nair@hostelhub.test")).orElseThrow();
        assertThat(r.blockName()).isEqualTo("A Block");
        assertThat(r.roomNo()).isEqualTo("A-103");
        assertThat(r.floorNo()).isEqualTo(1);
        assertThat(r.roomType()).isEqualTo(RoomType.TRIPLE);
        assertThat(r.startDate()).isEqualTo(dbToday().minusDays(90));
        assertThat(r.roommates()).extracting(Student::fullName).containsExactly("Nikhil Gupta", "Rahul Verma");
    }

    @Test
    void fr06_unallocatedStudentHasNoRoom() {
        assertThat(students.myRoom(login("karan.mehta@hostelhub.test"))).isEmpty();
    }
}
