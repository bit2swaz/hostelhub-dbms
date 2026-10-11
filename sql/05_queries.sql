-- HostelHub: 05_queries.sql
-- Run as hostelhub after 04_seed.sql. Demo queries for SQL*Plus:
-- joins, aggregates and views, then deliberate constraint violations.
-- Every violation is preceded by an "-- Expected: ORA-xxxxx" comment; those errors
-- ARE the demo. The script ends with ROLLBACK, so the data is unchanged.

SET DEFINE OFF
SET LINESIZE 200
SET PAGESIZE 100
COLUMN full_name FORMAT A22
COLUMN student_name FORMAT A22
COLUMN block_name FORMAT A10
COLUMN room_no FORMAT A7
COLUMN category_name FORMAT A13
COLUMN staff_name FORMAT A15
COLUMN status FORMAT A12
COLUMN programme FORMAT A45

PROMPT ===== 1. Join: active residents with block and room =====
SELECT s.reg_no, u.full_name, b.block_name, a.room_no, r.room_type, a.start_date
FROM allocation a
JOIN student s     ON s.user_id = a.student_id
JOIN hostel_user u ON u.user_id = s.user_id
JOIN room r        ON r.block_id = a.block_id AND r.room_no = a.room_no
JOIN block b       ON b.block_id = a.block_id
WHERE a.status = 'ACTIVE'
ORDER BY b.block_name, a.room_no, s.reg_no;

PROMPT ===== 2. Aggregate: beds, occupancy and vacancies per block (V_ROOM_OCCUPANCY) =====
SELECT block_name, COUNT(*) AS rooms, SUM(capacity) AS beds,
       SUM(occupancy) AS occupied, SUM(vacancies) AS vacant
FROM v_room_occupancy
GROUP BY block_name
ORDER BY block_name;

PROMPT ===== 3. View: rooms that are exactly full =====
SELECT block_name, room_no, room_type, capacity, occupancy
FROM v_room_occupancy
WHERE vacancies = 0
ORDER BY block_name, room_no;

PROMPT ===== 4. Aggregate: complaints per category and status =====
SELECT c.category_name, cp.status, COUNT(*) AS complaints
FROM complaint cp
JOIN category c ON c.category_id = cp.category_id
GROUP BY c.category_name, cp.status
ORDER BY c.category_name, cp.status;

PROMPT ===== 5. View: overdue complaints (V_OVERDUE_COMPLAINTS, BR-6) =====
SELECT complaint_id, block_name, room_no, category_name, status, staff_name, sla_days, days_overdue
FROM v_overdue_complaints
ORDER BY days_overdue DESC;

PROMPT ===== 6. Outer join: open complaints with no staff assigned =====
SELECT cp.complaint_id, b.block_name, cp.room_no, c.category_name, cp.raised_on
FROM complaint cp
JOIN block b    ON b.block_id = cp.block_id
JOIN category c ON c.category_id = cp.category_id
LEFT JOIN staff st ON st.staff_id = cp.staff_id
WHERE cp.status = 'OPEN' AND st.staff_id IS NULL
ORDER BY cp.complaint_id;

PROMPT ===== 7. Subquery: students with no active allocation =====
SELECT s.reg_no, u.full_name, s.gender, s.programme
FROM student s
JOIN hostel_user u ON u.user_id = s.user_id
WHERE NOT EXISTS (SELECT 1 FROM allocation a WHERE a.student_id = s.user_id AND a.status = 'ACTIVE')
ORDER BY s.reg_no;

PROMPT ===== 8. Allocation history (VACATED rows) =====
SELECT s.reg_no, a.room_no, a.start_date, a.end_date, a.status
FROM allocation a
JOIN student s ON s.user_id = a.student_id
WHERE a.status = 'VACATED'
ORDER BY s.reg_no;

PROMPT
PROMPT ===== Constraint violations (each error below is expected) =====

PROMPT V1. BR-1: allocate a student to a full room (A-001 is a full SINGLE)
-- Expected: ORA-20001 (trg_alloc_capacity)
INSERT INTO allocation (student_id, block_id, room_no, approved_by, status)
SELECT s.user_id, b.block_id, 'A-001', w.user_id, 'ACTIVE'
FROM student s, block b, warden w
WHERE s.reg_no = '25BIT0633' AND b.block_name = 'A Block' AND w.block_id = b.block_id;

PROMPT V2. BR-2: second ACTIVE allocation for a student who already has one
-- Expected: ORA-00001 (ux_alloc_one_active)
INSERT INTO allocation (student_id, block_id, room_no, approved_by, status)
SELECT s.user_id, b.block_id, 'A-002', w.user_id, 'ACTIVE'
FROM student s, block b, warden w
WHERE s.reg_no = '25BCE1045' AND b.block_name = 'A Block' AND w.block_id = b.block_id;

PROMPT V3. BR-3: female student into a male block
-- Expected: ORA-20003 (trg_alloc_gender)
INSERT INTO allocation (student_id, block_id, room_no, approved_by, status)
SELECT s.user_id, b.block_id, 'A-002', w.user_id, 'ACTIVE'
FROM student s, block b, warden w
WHERE s.reg_no = '25BCE1498' AND b.block_name = 'A Block' AND w.block_id = b.block_id;

PROMPT V4. BR-4: B Block warden approves an allocation in A Block
-- Expected: ORA-20004 (trg_alloc_warden_block)
INSERT INTO allocation (student_id, block_id, room_no, approved_by, status)
SELECT s.user_id, b.block_id, 'A-002', w.user_id, 'ACTIVE'
FROM student s, block b, warden w, block wb
WHERE s.reg_no = '25BIT0633' AND b.block_name = 'A Block'
  AND wb.block_name = 'B Block' AND w.block_id = wb.block_id;

PROMPT V5. BR-4: a second warden for A Block
-- Expected: ORA-00001 (uq_warden_block)
UPDATE warden SET block_id = (SELECT block_id FROM block WHERE block_name = 'A Block')
WHERE block_id = (SELECT block_id FROM block WHERE block_name = 'B Block');

PROMPT V6. BR-5: invalid complaint status
-- Expected: ORA-02290 (ck_complaint_status)
UPDATE complaint SET status = 'CLOSED'
WHERE description = 'Bed frame is broken on one side.';

PROMPT V7. BR-5: resolved_on before raised_on
-- Expected: ORA-02290 (ck_complaint_dates)
UPDATE complaint SET resolved_on = raised_on - 1
WHERE description = 'Bathroom tap is leaking continuously.';

PROMPT V8. Work cannot start without assigned staff
-- Expected: ORA-02290 (ck_complaint_staff)
UPDATE complaint SET status = 'IN_PROGRESS'
WHERE description = 'Washbasin drain is blocked and water does not flow out.';

PROMPT V9. Assigned staff must match the complaint category (Plumber on an Electrical complaint)
-- Expected: ORA-20005 (trg_complaint_staff_cat)
UPDATE complaint SET staff_id = (SELECT staff_id FROM staff WHERE full_name = 'Mahesh Gowda')
WHERE description = 'Tube light in the room flickers constantly.';

PROMPT V10. Room capacity must match room type
-- Expected: ORA-02290 (ck_room_capacity_type)
INSERT INTO room (block_id, room_no, floor_no, room_type, capacity)
SELECT block_id, 'A-304', 3, 'DOUBLE', 3 FROM block WHERE block_name = 'A Block';

PROMPT V11. Invalid registration number format
-- Expected: ORA-02290 (ck_student_reg_no)
UPDATE student SET reg_no = '25bce1045' WHERE reg_no = '25BCE1045';

PROMPT V12. Phone number must be 10 digits
-- Expected: ORA-02290 (ck_hostel_user_phone)
UPDATE hostel_user SET phone = '98400' WHERE email = 'warden.a@hostelhub.test';

PROMPT V13. Duplicate email
-- Expected: ORA-00001 (uq_hostel_user_email)
UPDATE hostel_user SET email = 'warden.a@hostelhub.test' WHERE email = 'warden.b@hostelhub.test';

PROMPT V14. Complaint description shorter than 10 characters
-- Expected: ORA-02290 (ck_complaint_description)
UPDATE complaint SET description = 'Broken'
WHERE description = 'Bed frame is broken on one side.';

PROMPT V15. Room in a block that does not exist
-- Expected: ORA-02291 (fk_room_block)
INSERT INTO room (block_id, room_no, floor_no, room_type, capacity)
VALUES (-1, 'X-001', 0, 'SINGLE', 1);

PROMPT V16. Delete a room that has allocation history
-- Expected: ORA-02292 (fk_allocation_room)
DELETE FROM room
WHERE room_no = 'A-102' AND block_id = (SELECT block_id FROM block WHERE block_name = 'A Block');

PROMPT V17. Delete a student who has allocation history
-- Expected: ORA-02292 (fk_allocation_student)
DELETE FROM student WHERE reg_no = '24BME0518';

ROLLBACK;
PROMPT ===== Done. All changes rolled back. =====
