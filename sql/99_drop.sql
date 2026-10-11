-- HostelHub: 99_drop.sql
-- Run as hostelhub. Drops every object in reverse dependency order.
-- Safe to re-run: objects that do not exist are skipped.
-- Triggers and indexes are dropped with their tables.

SET DEFINE OFF

BEGIN
  FOR v IN (SELECT 'v_overdue_complaints' AS name FROM dual
            UNION ALL SELECT 'v_room_occupancy' FROM dual) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP VIEW ' || v.name;
    EXCEPTION
      WHEN OTHERS THEN
        IF SQLCODE <> -942 THEN RAISE; END IF;
    END;
  END LOOP;
END;
/

BEGIN
  FOR t IN (SELECT 'complaint' AS name, 1 AS ord FROM dual
            UNION ALL SELECT 'staff', 2 FROM dual
            UNION ALL SELECT 'category', 3 FROM dual
            UNION ALL SELECT 'allocation', 4 FROM dual
            UNION ALL SELECT 'room', 5 FROM dual
            UNION ALL SELECT 'warden', 6 FROM dual
            UNION ALL SELECT 'block', 7 FROM dual
            UNION ALL SELECT 'student', 8 FROM dual
            UNION ALL SELECT 'hostel_user', 9 FROM dual
            ORDER BY 2) LOOP
    BEGIN
      EXECUTE IMMEDIATE 'DROP TABLE ' || t.name || ' PURGE';
    EXCEPTION
      WHEN OTHERS THEN
        IF SQLCODE <> -942 THEN RAISE; END IF;
    END;
  END LOOP;
END;
/
