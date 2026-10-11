#!/usr/bin/env python3
"""Builds docs/TEST_CASES.md from the surefire reports of the last `mvn verify`.

Usage (from the project root, after a full run):
    xvfb-run -a -s "-screen 0 1280x800x24" mvn verify
    python3 docs/gen_test_cases.py

Actual and Result come from target/surefire-reports. A case whose test failed, errored,
was skipped or did not run is written as FAIL / NOT RUN, never as PASS.
"""
import datetime
import glob
import re
import xml.etree.ElementTree as ET

T = '../src/test/java/com/hostelhub/'

# Index of each constraint case, in the order ConstraintTest.constraintCases() lists them.
src = open('src/test/java/com/hostelhub/db/ConstraintTest.java').read()
block = src[src.index('static Stream<Case> constraintCases()'):src.index('static Stream<Case> notNullCases()')]
CONSTRAINT_INDEX = {name: i + 1 for i, name in enumerate(re.findall(r'named\("(\w+)"', block))}


def ck(name):
    return ('db.ConstraintTest', f'constraintRejectsViolation(Case)[{CONSTRAINT_INDEX[name]}]')


# (type, covers, steps, expected, actual-when-passed, (class, method))
CASES = [
    ('Functional', 'FR-01', 'Log in as warden.b@hostelhub.test / Hostel@123.',
     'Warden session for B Block; Warden dashboard opens.', 'Role WARDEN, block_id = B Block, name Dr. Lakshmi Menon.',
     ('service.AuthServiceTest', 'fr01_wardenLoginCarriesOwnBlock')),
    ('Functional', 'FR-01', 'Log in as arjun.nair@hostelhub.test / Hostel@123.',
     'Student session with no block; Student dashboard opens.', 'Role STUDENT, block_id null.',
     ('service.AuthServiceTest', 'fr01_studentLoginHasNoBlock')),
    ('Error handling', 'FR-01', 'Log in with a wrong password, then with an unknown email.',
     'Both: "Invalid email or password." (no hint which part is wrong).', 'Both returned "Invalid email or password.".',
     ('service.AuthServiceTest', 'fr01_wrongPasswordAndUnknownEmailGiveTheSameGenericMessage')),
    ('UI', 'FR-01', 'Login screen: enter a wrong password, click Log in.',
     'Red inline message "Invalid email or password."; login window stays open.', 'Message shown; window still open.',
     ('ui.GuiFlowsTest', 'fr01_badLoginShowsError')),
    ('Functional', 'FR-02', 'Warden A opens Block & Rooms.',
     '12 rooms, all A-xxx, total occupancy 14 (from V_ROOM_OCCUPANCY).', '12 A-block rooms, occupancy sum 14.',
     ('service.RoomServiceTest', 'fr02_roomsAreOnlyTheWardensBlockWithLiveOccupancy')),
    ('Functional', 'FR-02', 'Add room "a-304", floor 3, DOUBLE.',
     'Room A-304 saved with capacity 2, occupancy 0.', 'A-304 present, capacity 2, occupancy 0, floor 3.',
     ('service.RoomServiceTest', 'fr02_addRoomDerivesCapacityFromType')),
    ('Error handling', 'FR-02', 'Add A-101 (exists); add a room on floor 4 of a 4-floor block; add room "A 401!".',
     'Messages: "Room A-101 already exists in A Block.", "Floor must be between 0 and 3 for this block.", '
     '"Room number must be 1-6 letters, digits or \'-\'."', 'All three exact messages returned; nothing written.',
     ('service.RoomServiceTest', 'fr02_addRoomValidation')),
    ('Functional', 'FR-02', 'Delete room A-102 (has a VACATED allocation) through the service.',
     '"Room A-102 has allocation or complaint history and cannot be deleted."', 'Exact message; room kept.',
     ('service.RoomServiceTest', 'fr02_deleteRoomWithHistoryIsBlocked')),
    ('Constraint', 'FR-02', 'DELETE FROM room WHERE room_no = \'A-102\' directly in SQL.',
     'ORA-02292 fk_allocation_room.', 'ORA-02292 integrity constraint (FK_ALLOCATION_ROOM) violated.',
     ('db.ConstraintTest', 'deletingRoomWithHistoryIsBlocked')),
    ('UI', 'FR-02', 'Block & Rooms: select A-102, Delete room, confirm Yes.',
     'Confirmation dialog, then error dialog with the history message; A-102 still exists.',
     'Confirm dialog shown, error dialog shown with exact message, row kept.',
     ('ui.GuiFlowsTest', 'fr02_deletingARoomWithHistoryIsBlockedWithAMessage')),
    ('UI', 'FR-02, FR-03', 'Set floors to 5 and Save block; add room A-401 (floor 4, DOUBLE); add student Rhea Kapoor; delete her.',
     '"Saved."; 13 rooms; 46 then 45 students; A-401 capacity 2; num_floors 5.', 'All values as expected.',
     ('ui.GuiFlowsTest', 'fr02_fr03_wardenEditsBlockAddsRoomAndRegistersThenDeletesAStudent')),
    ('UI', 'FR-02, FR-03, BR-1', 'Edit room A-103 to SINGLE (3 occupants); edit A-002 to DOUBLE; edit student 25BIT0633: name "Naveen K", year 2.',
     'Inline "Room A-103 has 3 occupants; a SINGLE room holds only 1 (BR-1)."; A-002 capacity 2; student row and database updated.',
     'As expected; reg no field locked; A-002 capacity 2; year_of_study 2.',
     ('ui.GuiFlowsTest', 'fr02_fr03_wardenEditsARoomAndAStudent')),
    ('Functional', 'FR-03', 'Register student 25bce2001 with initial password Welcome@2025.',
     'HOSTEL_USER + STUDENT rows in one transaction; reg_no upper-cased; password stored as PBKDF2 hash; can log in.',
     'Rows created, reg_no 25BCE2001, hash starts pbkdf2$, login works.',
     ('service.StudentServiceTest', 'fr03_createInsertsUserAndStudentWithHashedPassword')),
    ('Error handling', 'FR-03', 'Register with a used email, a used reg_no, reg_no "BCE1045", phone "12345", year 6, password "short".',
     'Six exact messages. No statement goes to the database.', 'All six exact messages; no SQLException cause.',
     ('service.StudentServiceTest', 'fr03_createRejectsDuplicatesAndBadInputBeforeTheDatabase')),
    ('Functional', 'FR-03', 'Search "25bce1045", then "sharma", then empty.',
     'Arjun Nair (A Block, A-103); Priya Sharma; all 45 students.', '1, 1 and 45 rows as expected.',
     ('service.StudentServiceTest', 'fr03_searchByRegNoOrNameIsCaseInsensitiveAndShowsCurrentRoom')),
    ('Functional', 'FR-03', 'Delete Kabir Sethi (24BME0518, has allocation history).',
     '"Student 24BME0518 has allocation or complaint history and cannot be deleted."', 'Exact message; student kept.',
     ('service.StudentServiceTest', 'fr03_deleteWithHistoryIsBlocked')),
    ('Functional', 'FR-04', 'Warden A allocates Naveen Kumar (25BIT0633) to A-202.',
     'ACTIVE allocation, approved_by = warden A, start_date today; A-202 occupancy 2.', 'As expected.',
     ('service.AllocationServiceTest', 'fr04_allocateCreatesActiveAllocationApprovedByWarden')),
    ('Functional', 'FR-04, BR-2, BR-3', 'Open the allocate pickers as warden A and warden B.',
     'A: only the 4 unallocated male students; B: only the 3 unallocated female students; rooms only with vacancies.',
     'A: 24BME0518, 25BCE1420, 25BCE1477, 25BIT0633. B: 24BEC0587, 25BCE1498, 25BIT0702. No full rooms listed.',
     ('service.AllocationServiceTest', 'fr04_pickersAreFilteredByGenderVacancyAndActiveAllocation')),
    ('UI', 'FR-04', 'Allocations: Allocate room..., pick 25BIT0633 and A-202, Allocate.',
     'Dialog closes; history shows 16 rows; DB has the ACTIVE row.', 'As expected.',
     ('ui.GuiFlowsTest', 'fr04_wardenAllocatesAStudentToARoom')),
    ('Functional', 'FR-05', 'Vacate Arjun Nair\'s allocation, then vacate it again.',
     'Status VACATED, end_date today, A-103 occupancy 2; second try: "This allocation has already been vacated."',
     'As expected.', ('service.AllocationServiceTest', 'fr05_vacateKeepsHistory')),
    ('Functional', 'FR-05', 'Transfer Arjun Nair from A-103 to A-202.',
     'Old row VACATED with end_date, new ACTIVE row in A-202, in one transaction.', 'As expected; occupancies 2 and 2.',
     ('service.AllocationServiceTest', 'fr05_transferVacatesAndReallocatesInOneTransaction')),
    ('Functional', 'FR-05', 'Transfer Arjun Nair to A-001 (full). The vacate runs first inside the transaction.',
     'BR-1 message; rollback leaves the original allocation ACTIVE with no end_date and no new rows.',
     '"Room A-001 (A Block) is full (BR-1)."; original still ACTIVE; row count unchanged.',
     ('service.AllocationServiceTest', 'failedTransferRollsBackTheVacate')),
    ('UI', 'FR-05', 'Allocations: select Arjun, Transfer... to A-302; then Vacate the new row; filter Vacated.',
     'New ACTIVE row in A-302; then vacated; Vacated filter shows 3 rows.', 'As expected; 2 VACATED rows for Arjun in DB.',
     ('ui.GuiFlowsTest', 'fr05_wardenTransfersThenVacatesKeepingHistory')),
    ('Functional', 'FR-06', 'Student Arjun Nair opens My Room.',
     'A Block, A-103, floor 1, TRIPLE, since 90 days ago, roommates Nikhil Gupta and Rahul Verma.', 'As expected.',
     ('service.StudentServiceTest', 'fr06_myRoomShowsRoomAndRoommates')),
    ('UI', 'FR-06', 'Log in as karan.mehta@hostelhub.test (no allocation).',
     '"No room allocated yet" empty state.', 'Empty state shown.',
     ('ui.GuiFlowsTest', 'fr06_unallocatedStudentSeesEmptyState')),
    ('Functional', 'FR-07', 'Arjun raises an Electrical complaint "  Switch board in the room is loose.  ".',
     'OPEN complaint for A-103 (room taken from the allocation), description trimmed, no staff, not overdue.', 'As expected.',
     ('service.ComplaintServiceTest', 'fr07_raiseUsesTheStudentsActiveRoom')),
    ('Error handling', 'FR-07', 'Raise as an unallocated student; with an 8-character description; as a warden.',
     '"You need an active room allocation to raise a complaint.", "Description must be at least 10 characters.", '
     '"Only students can do this."', 'All three exact messages.',
     ('service.ComplaintServiceTest', 'fr07_raiseRules')),
    ('UI', 'FR-07, FR-10', 'My Complaints: Raise complaint..., Electrical, "Short" (rejected inline), then a valid text.',
     'Inline "Description must be at least 10 characters."; then the new complaint appears in the table.', 'As expected.',
     ('ui.GuiFlowsTest', 'fr07_fr10_studentRaisesComplaintAndSeesItInTheTable')),
    ('Functional', 'FR-08', 'Warden C opens Assign staff for the Plumbing complaint in C-002 and assigns Mahesh Gowda.',
     'Picker lists only Plumbing staff (Joseph Thomas, Mahesh Gowda); staff saved.', 'As expected.',
     ('service.ComplaintServiceTest', 'fr08_staffPickerAndAssignmentAreCategoryMatched')),
    ('Error handling', 'FR-08', 'Assign Ravi Kumar (Electrical) to the Plumbing complaint through the service.',
     '"Ravi Kumar handles Electrical, not Plumbing complaints."', 'Exact message; no SQLException cause.',
     ('service.ComplaintServiceTest', 'fr08_wrongCategoryStaffIsRejected')),
    ('Constraint', 'FR-08', 'UPDATE complaint SET staff_id = Mahesh Gowda (Plumbing) on an Electrical complaint.',
     'ORA-20005 from trg_complaint_staff_cat.', 'ORA-20005: Assigned staff must belong to the complaint\'s category.',
     ('db.TriggerTest', 'staffCategoryMismatchOnUpdateRaises20005')),
    ('Functional', 'FR-09', 'Assign staff, then move OPEN -> IN_PROGRESS -> RESOLVED.',
     'IN_PROGRESS with resolved_on null; RESOLVED with resolved_on = now >= raised_on; leaves V_OVERDUE_COMPLAINTS.',
     'As expected.', ('service.ComplaintServiceTest', 'fr09_statusMovesForwardAndResolvedSetsResolvedOn')),
    ('Error handling', 'FR-09', 'Move an OPEN complaint with no staff to IN_PROGRESS.',
     '"Assign staff before starting work on this complaint."', 'Exact message.',
     ('service.ComplaintServiceTest', 'fr09_workCannotStartWithoutStaff')),
    ('UI', 'FR-08, FR-09', 'Complaints (warden C): Assign staff... Joseph Thomas; Advance status two times and confirm each change.',
     'Staff shown; status IN_PROGRESS then RESOLVED; DB row RESOLVED with resolved_on.', 'As expected.',
     ('ui.GuiFlowsTest', 'fr08_fr09_wardenAssignsStaffAndResolvesAComplaint')),
    ('Functional', 'FR-10, BR-6', 'Priya Sharma opens My Complaints.',
     'Tube light: OPEN, Sunil Das, overdue; Study table: RESOLVED, Babu Rajan, not overdue.', 'As expected.',
     ('service.ComplaintServiceTest', 'fr10_trackOwnComplaintsWithOverdueFlag')),
    ('Functional', 'FR-11', 'Warden A opens Reports.',
     'Rooms 12, beds 28, occupied 14, vacant 14, open complaints 3, overdue 2.', 'ReportSummary(12, 28, 14, 14, 3, 2).',
     ('service.ReportServiceTest', 'fr11_summaryCountsForOwnBlock')),
    ('Functional', 'FR-11', 'Vacancy report for A Block.', '12 rooms from V_ROOM_OCCUPANCY; A-201 has 1 vacancy; 14 vacancies in total.',
     'As expected.', ('service.ReportServiceTest', 'fr11_vacancyReportComesFromOccupancyView')),
    ('Functional', 'FR-11, BR-6', 'Open and overdue complaint reports for A Block.',
     '3 open; overdue: Electrical (3 days), Internet (2 days), most overdue first.', 'As expected.',
     ('service.ReportServiceTest', 'fr11_openAndOverdueComplaints')),
    ('Error handling', 'BR-1', 'Service: allocate Naveen Kumar to full room A-001.',
     '"Room A-001 (A Block) is full (BR-1)." before any SQL write.', 'Exact message; no SQLException cause.',
     ('service.BusinessRulesTest', 'br1_service_rejectsFullRoom')),
    ('Constraint', 'BR-1', 'SQL: INSERT an ACTIVE allocation into A-001.',
     'ORA-20001 from trg_alloc_capacity.', 'ORA-20001: Room A-001 (A Block) is full (BR-1).',
     ('service.BusinessRulesTest', 'br1_database_trgAllocCapacity')),
    ('Functional', 'BR-1', 'Four threads allocate four different students to the last bed of A-201 at the same moment.',
     'Exactly one succeeds; three get the BR-1 message; occupancy 3.', 'One success, three BR-1 rejections, occupancy 3.',
     ('service.AllocationServiceTest', 'concurrentAllocationOfLastBedOnlyOneSucceeds')),
    ('UI', 'BR-1', 'Allocate dialog open with A-201 (1 of 3 free) selected; another session takes that bed; click Allocate.',
     'Inline "Room A-201 (A Block) is full (BR-1)."; dialog stays open.', 'As expected.',
     ('ui.GuiFlowsTest', 'br1_allocatingToARoomThatJustFilledShowsTheBr1Message')),
    ('Error handling', 'BR-2', 'Service: allocate Arjun Nair (already ACTIVE) to A-002.',
     '"Student 25BCE1045 already has an active allocation (BR-2)."', 'Exact message; no SQLException cause.',
     ('service.BusinessRulesTest', 'br2_service_rejectsSecondActiveAllocation')),
    ('Constraint', 'BR-2', 'SQL: INSERT a second ACTIVE allocation for Arjun Nair.',
     'ORA-00001 on ux_alloc_one_active.', 'ORA-00001 unique constraint (UX_ALLOC_ONE_ACTIVE) violated.',
     ('service.BusinessRulesTest', 'br2_database_uxAllocOneActive')),
    ('Functional', 'BR-2', 'Two threads allocate the same student to A-002 and A-302 at the same moment.',
     'Exactly one succeeds; the other gets the BR-2 message (from the service or mapped from ux_alloc_one_active).',
     'One success; one "... already has an active allocation (BR-2)."',
     ('service.AllocationServiceTest', 'concurrentAllocationOfSameStudentToTwoRoomsOnlyOneSucceeds')),
    ('Error handling', 'BR-3', 'Service: warden B allocates a male student to B-002.',
     '"Student gender does not match the block\'s gender (BR-3)."; picker lists only female students.', 'As expected.',
     ('service.BusinessRulesTest', 'br3_service_rejectsGenderMismatch')),
    ('Constraint', 'BR-3', 'SQL: INSERT a male student into B Block.',
     'ORA-20003 from trg_alloc_gender.', 'ORA-20003: Student gender does not match the block\'s gender (BR-3).',
     ('service.BusinessRulesTest', 'br3_database_trgAllocGender')),
    ('Error handling', 'BR-4', 'Warden B tries to vacate, transfer, assign staff, change status and allocate in A Block.',
     '"You can only manage ... in your own block (BR-4)." / "Room A-002 was not found in your block."; student session: "Only wardens can do this."',
     'All six exact messages.', ('service.BusinessRulesTest', 'br4_service_rejectsActionsOnAnotherBlock')),
    ('Constraint', 'BR-4', 'SQL: C Block warden approves an A Block allocation; make B\'s warden manage A Block.',
     'ORA-20004 from trg_alloc_warden_block; ORA-00001 on uq_warden_block.', 'Both exact errors raised.',
     ('service.BusinessRulesTest', 'br4_database_trgAllocWardenBlockAndUqWardenBlock')),
    ('Error handling', 'BR-5', 'Service: IN_PROGRESS -> OPEN, IN_PROGRESS -> IN_PROGRESS, OPEN -> RESOLVED, RESOLVED -> IN_PROGRESS.',
     'Forward-only message for the first three; "Complaint #n is already resolved (BR-5)." for the last.', 'All four exact messages.',
     ('service.BusinessRulesTest', 'br5_service_forwardOnlyStateMachine')),
    ('Constraint', 'BR-5', 'SQL: status \'CLOSED\'; resolved_on before raised_on; RESOLVED without resolved_on.',
     'ORA-02290 on ck_complaint_status, ck_complaint_dates and ck_complaint_resolved.', 'All three exact errors.',
     ('service.BusinessRulesTest', 'br5_database_ckComplaintStatusAndDates')),
    ('Functional', 'BR-6', 'Service: list complaints for Arjun Nair, Varun Menon and warden C\'s overdue report.',
     'Arjun: both overdue; Varun (Carpentry, 1 day, SLA 5): not overdue; C Block: only the washbasin complaint.', 'As expected.',
     ('service.BusinessRulesTest', 'br6_service_overdueFlagMatchesSla')),
    ('Constraint', 'BR-6', 'SQL: query V_OVERDUE_COMPLAINTS after 04_seed.sql.',
     '4 rows, none RESOLVED, no negative days_overdue.', '4 rows; 0 resolved; 0 negative.',
     ('service.BusinessRulesTest', 'br6_database_vOverdueComplaints')),
    ('Constraint', 'BR-6', 'SQL: set a Carpentry complaint\'s raised_on to 5 days minus 1 hour, then 5 days plus 1 hour ago.',
     'Not in V_OVERDUE_COMPLAINTS, then in it.', 'As expected.',
     ('db.ViewTest', 'br6_complaintBecomesOverdueOnlyAfterSlaDays')),
    ('Constraint', 'BR-1', 'Compare V_ROOM_OCCUPANCY with a direct count of ACTIVE allocations for all 36 rooms.',
     'Equal for every room; vacancies = capacity - occupancy.', 'Equal for all 36 rooms.',
     ('db.ViewTest', 'occupancyEqualsActiveAllocationCountForEveryRoom')),
    ('Constraint', '-', 'INSERT a DOUBLE room with capacity 3.', 'ORA-02290 ck_room_capacity_type.',
     'ORA-02290 check constraint (CK_ROOM_CAPACITY_TYPE) violated.', ck('ck_room_capacity_type')),
    ('Constraint', 'FR-03', 'UPDATE a reg_no to lower case "25bce1189".', 'ORA-02290 ck_student_reg_no.',
     'ORA-02290 check constraint (CK_STUDENT_REG_NO) violated.', ck('ck_student_reg_no')),
    ('Constraint', 'FR-03', 'INSERT a user with an email that is already in use.', 'ORA-00001 uq_hostel_user_email.',
     'ORA-00001 unique constraint (UQ_HOSTEL_USER_EMAIL) violated.', ck('uq_hostel_user_email')),
    ('Constraint', 'FR-02', 'INSERT a room into block_id -1.', 'ORA-02291 fk_room_block.',
     'ORA-02291 integrity constraint (FK_ROOM_BLOCK) violated - parent key not found.', ck('fk_room_block')),
    ('Constraint', 'FR-09', 'UPDATE an OPEN complaint with no staff to IN_PROGRESS in SQL.', 'ORA-02290 ck_complaint_staff.',
     'ORA-02290 check constraint (CK_COMPLAINT_STAFF) violated.', ck('ck_complaint_staff')),
    ('Constraint', 'all', 'List every constraint and the unique index from USER_CONSTRAINTS / USER_INDEXES.',
     'All 87 (48 PK/FK/UQ/CK + 38 NOT NULL + ux_alloc_one_active) have a violation test with the exact ORA code.',
     '87 of 87 covered; the 87 violation tests passed in this run.',
     ('db.ConstraintTest', 'everyConstraintAndUniqueIndexHasACase')),
    ('Error handling', '-', 'Map an unknown ORA-00942 and a connection failure to user messages.',
     '"Database error (ORA-00942). Please try again." and "Cannot connect to the database..."; no stack trace text.',
     'As expected.', ('util.OracleErrorMapperTest', 'unknownErrorsShowCodeButNoInternals')),
    ('Functional', 'all', 'Run sql/00 as SYSTEM, 01-05 as hostelhub, 99 twice, 01-04 again, all in real SQL*Plus.',
     'No ORA/SP2/PLS errors except in 05, where the 17 errors match their "-- Expected" comments in order.',
     'Clean runs; 17 of 17 expected errors matched.', ('db.SqlPlusScriptsTest', 'scriptsRunCleanInSqlPlusInOrderAndAfterDrop')),
    ('Functional', 'all', 'Compare USER_TAB_COLUMNS with PRD section 5.1.', 'Every table and column name, type, length and nullability match.',
     '48 columns match exactly.', ('db.SchemaTest', 'tablesAndColumnsMatchPrd')),
    ('Functional', 'FR-01', 'Read every HOSTEL_USER.password after 04_seed.sql.',
     '48 distinct PBKDF2 hashes (pbkdf2$600000$salt$hash), each matches Hostel@123; no plain text.', 'As expected.',
     ('db.SchemaTest', 'seedPasswordsAreOnlyPbkdf2HashesOfTheDemoPassword')),
]


def load_results():
    results = {}
    for f in glob.glob('target/surefire-reports/TEST-*.xml'):
        for tc in ET.parse(f).getroot().iter('testcase'):
            cls = tc.get('classname').replace('com.hostelhub.', '')
            bad = tc.find('failure') if tc.find('failure') is not None else tc.find('error')
            if bad is not None:
                status = ('FAIL', (bad.get('message') or bad.get('type') or '').splitlines()[0][:200])
            elif tc.find('skipped') is not None:
                status = ('NOT RUN', 'skipped')
            else:
                status = ('PASS', tc.get('time'))
            results[(cls, tc.get('name'))] = status
    return results


def main():
    results = load_results()
    if not results:
        raise SystemExit('No surefire reports found. Run mvn verify first.')
    total = len(results)
    passed = sum(1 for s in results.values() if s[0] == 'PASS')
    covered = set()
    lines = [
        '# HostelHub test cases',
        '',
        'The script `docs/gen_test_cases.py` makes this file from the test reports of one full run:',
        f'`xvfb-run -a -s "-screen 0 1280x800x24" ./mvnw verify` on {datetime.date.today().isoformat()}.',
        'The run used Oracle XE 21c (in Testcontainers) and JDK 17.',
        '',
        f'In that run, {passed} of {total} automated tests passed. The Actual and Result columns come from that run.',
        'Each row links to the automated test that does the steps. All sample users have the password `Hostel@123`.',
        '',
        '| ID | Type | FR/BR covered | Steps | Expected | Actual | Result | Automated test |',
        '| --- | --- | --- | --- | --- | --- | --- | --- |',
    ]
    for i, (typ, cov, steps, expected, actual, (cls, method)) in enumerate(CASES, 1):
        status, detail = results.get((cls, method), ('NOT RUN', 'test not found in reports'))
        shown = actual if status == 'PASS' else f'{status}: {detail}'
        path = cls.replace('.', '/') + '.java'
        link = f'[{cls.split(".")[-1]}#{method.split("(")[0]}]({T}{path})'
        cell = lambda s: s.replace('|', '\\|')
        lines.append(f'| TC-{i:02d} | {typ} | {cov} | {cell(steps)} | {cell(expected)} | {cell(shown)} | **{status}** | {link} |')
        covered.update(c.strip() for c in cov.split(','))
    missing = [x for x in [f'FR-{n:02d}' for n in range(1, 12)] + [f'BR-{n}' for n in range(1, 7)] if x not in covered]
    if missing:
        raise SystemExit(f'Not covered: {missing}')
    lines += ['', f'There are {len(CASES)} cases. Each FR (FR-01 to FR-11) and each BR (BR-1 to BR-6) has one or more cases.',
              'BR-6 is a flag, not a rejection. Its cases prove that the view and the flag are correct.',
              '', 'Screenshots of each screen are in [screenshots/](screenshots/). '
              'Each GUI test also saves a screenshot to `target/screenshots/`.']
    open('docs/TEST_CASES.md', 'w').write('\n'.join(lines) + '\n')
    print(f'{len(CASES)} cases written; {passed}/{total} tests passed in the reports')


main()
