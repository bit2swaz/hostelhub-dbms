-- HostelHub: 00_create_user.sql
-- Run as SYSTEM, connected to the pluggable database
-- (XEPDB1 on Oracle XE 21c, FREEPDB1 on Oracle Database 23ai Free):
--   sqlplus system/<system_password>@localhost:1521/XEPDB1 @sql/00_create_user.sql
--
-- 'hostelhub' is a development password for the DB account only.
-- Change it for any shared machine, and set HOSTELHUB_DB_PASSWORD to match.
-- Application user passwords are never stored in plain text (PBKDF2 hashes).

SET DEFINE OFF

CREATE USER hostelhub IDENTIFIED BY hostelhub
  DEFAULT TABLESPACE users
  QUOTA 100M ON users;

GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE TRIGGER, CREATE SEQUENCE TO hostelhub;
