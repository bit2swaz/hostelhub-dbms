-- HostelHub: 02_views.sql
-- Run as hostelhub. Creates the views (PRD section 5.2).

SET DEFINE OFF

-- Derived occupancy attribute from the ER model. Never stored.
CREATE OR REPLACE VIEW v_room_occupancy AS
SELECT r.block_id,
       b.block_name,
       r.room_no,
       r.floor_no,
       r.room_type,
       r.capacity,
       COUNT(a.alloc_id)              AS occupancy,
       r.capacity - COUNT(a.alloc_id) AS vacancies
FROM room r
JOIN block b ON b.block_id = r.block_id
LEFT JOIN allocation a
       ON a.block_id = r.block_id
      AND a.room_no = r.room_no
      AND a.status = 'ACTIVE'
GROUP BY r.block_id, b.block_name, r.room_no, r.floor_no, r.room_type, r.capacity;

-- BR-6: a complaint is overdue once it has been unresolved longer than its category's sla_days.
CREATE OR REPLACE VIEW v_overdue_complaints AS
SELECT c.complaint_id,
       c.student_id,
       u.full_name                                    AS student_name,
       s.reg_no,
       c.block_id,
       b.block_name,
       c.room_no,
       r.floor_no,
       c.category_id,
       cat.category_name,
       cat.sla_days,
       c.staff_id,
       st.full_name                                   AS staff_name,
       c.description,
       c.status,
       c.raised_on,
       FLOOR(SYSDATE - c.raised_on - cat.sla_days)    AS days_overdue
FROM complaint c
JOIN category cat   ON cat.category_id = c.category_id
JOIN student s      ON s.user_id = c.student_id
JOIN hostel_user u  ON u.user_id = c.student_id
JOIN room r         ON r.block_id = c.block_id AND r.room_no = c.room_no
JOIN block b        ON b.block_id = c.block_id
LEFT JOIN staff st  ON st.staff_id = c.staff_id
WHERE c.status <> 'RESOLVED'
  AND SYSDATE - c.raised_on > cat.sla_days;
