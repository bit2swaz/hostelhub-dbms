-- HostelHub: 03_triggers.sql
-- Run as hostelhub. Creates the triggers (PRD section 5.3).
-- Each trigger raises a named application error whose text is shown to the user.

SET DEFINE OFF

-- BR-1: no room's ACTIVE allocation count may exceed its capacity.
-- Statement-level, so reading ALLOCATION here does not raise a mutating-table error.
-- The trigger examines every room once per statement. This is fast enough for one hostel.
CREATE OR REPLACE TRIGGER trg_alloc_capacity
AFTER INSERT OR UPDATE ON allocation
DECLARE
  v_room VARCHAR2(60);
BEGIN
  SELECT MIN('Room ' || r.room_no || ' (' || b.block_name || ')')
    INTO v_room
    FROM room r
    JOIN block b ON b.block_id = r.block_id
   WHERE r.capacity < (SELECT COUNT(*)
                         FROM allocation a
                        WHERE a.block_id = r.block_id
                          AND a.room_no = r.room_no
                          AND a.status = 'ACTIVE');
  IF v_room IS NOT NULL THEN
    RAISE_APPLICATION_ERROR(-20001, v_room || ' is full (BR-1).');
  END IF;
END;
/

-- BR-3: the student's gender must match the block's gender_type.
CREATE OR REPLACE TRIGGER trg_alloc_gender
BEFORE INSERT ON allocation
FOR EACH ROW
DECLARE
  v_student_gender student.gender%TYPE;
  v_block_gender   block.gender_type%TYPE;
BEGIN
  SELECT gender INTO v_student_gender FROM student WHERE user_id = :NEW.student_id;
  SELECT gender_type INTO v_block_gender FROM block WHERE block_id = :NEW.block_id;
  IF v_student_gender <> v_block_gender THEN
    RAISE_APPLICATION_ERROR(-20003, 'Student gender does not match the block''s gender (BR-3).');
  END IF;
EXCEPTION
  -- A missing student or block is reported by the foreign keys instead.
  WHEN NO_DATA_FOUND THEN NULL;
END;
/

-- BR-4: the approving warden must manage the allocation's block.
CREATE OR REPLACE TRIGGER trg_alloc_warden_block
BEFORE INSERT ON allocation
FOR EACH ROW
DECLARE
  v_block warden.block_id%TYPE;
BEGIN
  SELECT block_id INTO v_block FROM warden WHERE user_id = :NEW.approved_by;
  IF v_block <> :NEW.block_id THEN
    RAISE_APPLICATION_ERROR(-20004, 'A warden can only approve allocations in their own block (BR-4).');
  END IF;
EXCEPTION
  -- A missing warden is reported by fk_allocation_warden instead.
  WHEN NO_DATA_FOUND THEN NULL;
END;
/

-- Assigned staff must belong to the complaint's category.
CREATE OR REPLACE TRIGGER trg_complaint_staff_cat
BEFORE INSERT OR UPDATE OF staff_id ON complaint
FOR EACH ROW
WHEN (NEW.staff_id IS NOT NULL)
DECLARE
  v_category staff.category_id%TYPE;
BEGIN
  SELECT category_id INTO v_category FROM staff WHERE staff_id = :NEW.staff_id;
  IF v_category <> :NEW.category_id THEN
    RAISE_APPLICATION_ERROR(-20005, 'Assigned staff must belong to the complaint''s category.');
  END IF;
EXCEPTION
  -- A missing staff member is reported by fk_complaint_staff instead.
  WHEN NO_DATA_FOUND THEN NULL;
END;
/
