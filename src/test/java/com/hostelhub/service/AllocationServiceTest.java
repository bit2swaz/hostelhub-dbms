package com.hostelhub.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hostelhub.model.Allocation;
import com.hostelhub.model.Room;
import com.hostelhub.model.Student;
import com.hostelhub.support.ServiceTestBase;
import com.hostelhub.util.Session;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** FR-04 allocate, FR-05 vacate/transfer, plus the rollback and last-bed concurrency tests (PRD 11.2). */
class AllocationServiceTest extends ServiceTestBase {

    @Test
    void fr04_pickersAreFilteredByGenderVacancyAndActiveAllocation() {
        assertThat(allocations.eligibleStudents(wardenA())).extracting(Student::regNo)
                .containsExactly("24BME0518", "25BCE1420", "25BCE1477", "25BIT0633");
        assertThat(allocations.eligibleStudents(wardenB())).extracting(Student::regNo)
                .containsExactly("24BEC0587", "25BCE1498", "25BIT0702");
        List<Room> free = allocations.availableRooms(wardenA());
        assertThat(free).allMatch(r -> r.vacancies() > 0 && r.roomNo().startsWith("A-"))
                .extracting(Room::roomNo).doesNotContain("A-001", "A-101", "A-103").contains("A-201", "A-002");
    }

    @Test
    void fr04_allocateCreatesActiveAllocationApprovedByWarden() throws Exception {
        Session w = wardenA();
        long id = allocations.allocate(w, studentId("25BIT0633"), "A-202");
        assertThat(id).isEqualTo(activeAllocId("25BIT0633"));
        assertThat(occupancy("A-202")).isEqualTo(2);
        assertThat(queryLong("SELECT approved_by FROM allocation WHERE alloc_id = ?", id)).isEqualTo(w.userId());
        assertThat(allocations.history(w, "ACTIVE")).filteredOn(a -> a.allocId() == id).singleElement()
                .satisfies(a -> assertThat(a.startDate()).isEqualTo(dbToday()));
    }

    @Test
    void fr05_vacateKeepsHistory() throws Exception {
        long id = activeAllocId("25BCE1045");
        allocations.vacate(wardenA(), id);
        Allocation a = allocations.history(wardenA(), "VACATED").stream()
                .filter(x -> x.allocId() == id).findFirst().orElseThrow();
        assertThat(a.endDate()).isEqualTo(dbToday());
        assertThat(occupancy("A-103")).isEqualTo(2);
        assertServiceRejects(() -> allocations.vacate(wardenA(), id), "This allocation has already been vacated.");
    }

    @Test
    void fr05_transferVacatesAndReallocatesInOneTransaction() throws Exception {
        long oldId = activeAllocId("25BCE1045");
        long newId = allocations.transfer(wardenA(), oldId, "A-202");
        assertThat(activeAllocId("25BCE1045")).isEqualTo(newId);
        assertThat(queryLong("SELECT COUNT(*) FROM allocation WHERE alloc_id = ? AND status = 'VACATED' "
                + "AND end_date IS NOT NULL", oldId)).isEqualTo(1);
        assertThat(occupancy("A-103")).isEqualTo(2);
        assertThat(occupancy("A-202")).isEqualTo(2);
    }

    @Test
    void fr05_historyFiltersByStatus() {
        Session w = wardenA();
        List<Allocation> all = allocations.history(w, null);
        assertThat(all).hasSize(15).allMatch(a -> a.blockId() == all.get(0).blockId());
        assertThat(allocations.history(w, "ACTIVE")).hasSize(14).allMatch(Allocation::isActive);
        assertThat(allocations.history(w, "VACATED")).singleElement()
                .satisfies(a -> assertThat(a.roomNo()).isEqualTo("A-102"));
    }

    @Test
    void transferToSameRoomIsRejected() throws Exception {
        assertServiceRejects(() -> allocations.transfer(wardenA(), activeAllocId("25BCE1045"), "A-103"),
                "Arjun Nair is already in room A-103.");
    }

    /** PRD 11.2: a failed transfer leaves the original allocation ACTIVE. */
    @Test
    void failedTransferRollsBackTheVacate() throws Exception {
        long oldId = activeAllocId("25BCE1045");
        long rowsBefore = queryLong("SELECT COUNT(*) FROM allocation");
        // The vacate UPDATE runs first inside the transaction; then the full target room fails BR-1.
        assertServiceRejects(() -> allocations.transfer(wardenA(), oldId, "A-001"), "Room A-001 (A Block) is full (BR-1).");
        assertThat(queryLong("SELECT COUNT(*) FROM allocation WHERE alloc_id = ? AND status = 'ACTIVE' "
                + "AND end_date IS NULL", oldId)).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM allocation")).isEqualTo(rowsBefore);
        assertThat(occupancy("A-103")).isEqualTo(3);
    }

    /** PRD 11.2: several threads race for the last bed of A-201 (2 of 3 taken); exactly one wins. */
    @Test
    void concurrentAllocationOfLastBedOnlyOneSucceeds() throws Exception {
        Session w = wardenA();
        List<Long> racers = List.of(studentId("25BIT0633"), studentId("25BCE1420"),
                studentId("25BCE1477"), studentId("24BME0518"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(racers.size());
        List<Future<Long>> results = new ArrayList<>();
        for (long student : racers) {
            results.add(pool.submit(() -> {
                start.await();
                return allocations.allocate(w, student, "A-201");
            }));
        }
        start.countDown();
        List<Long> wins = Collections.synchronizedList(new ArrayList<>());
        List<String> losses = new ArrayList<>();
        for (Future<Long> f : results) {
            try {
                wins.add(f.get(60, TimeUnit.SECONDS));
            } catch (java.util.concurrent.ExecutionException e) {
                losses.add(e.getCause().getMessage());
            }
        }
        pool.shutdown();
        assertThat(wins).hasSize(1);
        assertThat(losses).hasSize(3).containsOnly("Room A-201 (A Block) is full (BR-1).");
        assertThat(occupancy("A-201")).isEqualTo(3);
    }

    /**
     * Two wardens' rooms are locked separately, so both may pass the service BR-2 check for the same
     * student at the same moment; ux_alloc_one_active then stops the second one. Either way, one wins.
     */
    @Test
    void concurrentAllocationOfSameStudentToTwoRoomsOnlyOneSucceeds() throws Exception {
        Session w = wardenA();
        long student = studentId("25BIT0633");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Long>> results = new ArrayList<>();
        for (String room : List.of("A-002", "A-302")) {
            results.add(pool.submit(() -> {
                start.await();
                return allocations.allocate(w, student, room);
            }));
        }
        start.countDown();
        int wins = 0;
        List<String> losses = new ArrayList<>();
        for (Future<Long> f : results) {
            try {
                f.get(60, TimeUnit.SECONDS);
                wins++;
            } catch (java.util.concurrent.ExecutionException e) {
                losses.add(e.getCause().getMessage());
            }
        }
        pool.shutdown();
        assertThat(wins).isEqualTo(1);
        assertThat(losses).singleElement().asString().endsWith("already has an active allocation (BR-2).");
        assertThat(queryLong("SELECT COUNT(*) FROM allocation WHERE student_id = ? AND status = 'ACTIVE'", student))
                .isEqualTo(1);
    }
}
