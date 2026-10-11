# HostelHub

HostelHub is a desktop app for hostel room allocation and maintenance complaints. It uses Java Swing for the screens and an Oracle database through JDBC.

| | |
| --- | --- |
| Course | BACSE202 Database Systems, VIT (CO5 course project) |
| Team | Aditya Mishra (25BCE2138), Ansh Gupta (25BCE0231), Shashwat Sharma (25BCE0652) |
| Faculty | Prof. Deepak Raj D. M., Slot A1 |

## Documents

| Document | Contents |
| --- | --- |
| [docs/DATABASE.md](docs/DATABASE.md) | ER diagram, tables, constraints, triggers and views. |
| [docs/TEST_CASES.md](docs/TEST_CASES.md) | 65 test cases with results from the last full test run. |
| [docs/screenshots/](docs/screenshots/) | A screenshot of each screen. |
| [PRD.md](PRD.md) | The requirements. |

## What the app does

| Role | Tasks |
| --- | --- |
| Student | See the room, the block and the roommates (FR-06). Raise a complaint for the room (FR-07). Follow the complaints and see which ones are overdue (FR-10). |
| Warden | Edit the block and its rooms (FR-02). Register and edit students (FR-03). Allocate, vacate and transfer rooms in the block (FR-04, FR-05). Assign staff to complaints and move them forward (FR-08, FR-09). See reports (FR-11). |

Both roles log in with an email and a password (FR-01).

## Business rules

The app enforces each rule two times. The service layer stops a bad change first and shows a clear message. The database stops it again with a constraint or a trigger.

| Rule | Database | Service layer |
| --- | --- | --- |
| BR-1: Occupancy is never more than capacity | `trg_alloc_capacity` (ORA-20001) | Locks the room row, counts, then rejects |
| BR-2: One ACTIVE allocation per student | `ux_alloc_one_active` (ORA-00001) | Looks for an active allocation first |
| BR-3: Student gender agrees with block gender | `trg_alloc_gender` (ORA-20003) | Lists only students of the correct gender, and checks again |
| BR-4: A warden works only in their own block | `uq_warden_block`, `trg_alloc_warden_block` (ORA-20004) | Limits every warden action to the block of the logged-in warden |
| BR-5: Status goes OPEN, then IN_PROGRESS, then RESOLVED | `ck_complaint_status`, `ck_complaint_dates`, `ck_complaint_resolved` | Allows only the next status |
| BR-6: A complaint is overdue after `sla_days` | `V_OVERDUE_COMPLAINTS` | Shows an overdue flag in tables and reports |

## Tools

All tools are free and open source, except Oracle Database XE, which is free to use.

Java 17, Maven (through the included `mvnw`), Swing with FlatLaf, Oracle Database XE 21c, the `ojdbc11` driver, JUnit 5, AssertJ, Testcontainers, AssertJ Swing, JaCoCo and GitHub Actions.

## Quick start

### 1. Install the tools

1. Install Java 17 (Eclipse Temurin) from https://adoptium.net/temurin/releases/?version=17.
2. Install Docker Desktop from https://www.docker.com/products/docker-desktop/.
3. Open Docker Desktop and wait until it shows "Engine running".

You do not install Maven. The project includes `mvnw`, which gets Maven for you.

### 2. Start the database

Open a terminal in the project folder (on Windows, use PowerShell). Type this command:

```
docker run -d --name hostelhub-oracle -p 1521:1521 -e ORACLE_PASSWORD=oracle -e TZ=Asia/Kolkata gvenzl/oracle-xe:21-slim-faststart
```

The first time, Docker downloads about 2 GB. Type `docker logs hostelhub-oracle` until the output shows `DATABASE IS READY TO USE!`.

### 3. Load the scripts in SQL*Plus

Type these commands, one at a time:

```
docker cp sql hostelhub-oracle:/tmp/sql
docker exec hostelhub-oracle bash -c "cd /tmp/sql && echo exit | sqlplus -s system/oracle@localhost/XEPDB1 @00_create_user.sql"
docker exec hostelhub-oracle bash -c "cd /tmp/sql && echo exit | sqlplus -s hostelhub/hostelhub@localhost/XEPDB1 @01_schema.sql"
docker exec hostelhub-oracle bash -c "cd /tmp/sql && echo exit | sqlplus -s hostelhub/hostelhub@localhost/XEPDB1 @02_views.sql"
docker exec hostelhub-oracle bash -c "cd /tmp/sql && echo exit | sqlplus -s hostelhub/hostelhub@localhost/XEPDB1 @03_triggers.sql"
docker exec hostelhub-oracle bash -c "cd /tmp/sql && echo exit | sqlplus -s hostelhub/hostelhub@localhost/XEPDB1 @04_seed.sql"
```

### 4. Start the app

On Windows, type `.\mvnw.cmd -q compile exec:java`. On macOS and Linux, type `./mvnw -q compile exec:java`.

In Apache NetBeans, click **File**, then **Open Project**, and select the project folder. Then press F6.

### 5. Log in

All demo accounts have the password `Hostel@123`.

| Who | Email |
| --- | --- |
| Warden of A Block (male block) | `warden.a@hostelhub.test` |
| Warden of B Block (female block) | `warden.b@hostelhub.test` |
| Warden of C Block (male block) | `warden.c@hostelhub.test` |
| Student in room A-103 | `arjun.nair@hostelhub.test` |
| Student in room B-101 | `priya.sharma@hostelhub.test` |
| Student without a room | `karan.mehta@hostelhub.test` |

The other students log in with `firstname.lastname@hostelhub.test`.

### Next time

Open Docker Desktop, type `docker start hostelhub-oracle`, wait 30 seconds, then do step 4. To get the original sample data again, type the `01_schema.sql` command from step 3 with `@99_drop.sql` in place of `@01_schema.sql`. Then type the `01_schema.sql` to `04_seed.sql` commands again.

### Macs with an Apple chip

Use the image `gvenzl/oracle-free:23-slim-faststart` in step 2. In step 3, change `XEPDB1` to `FREEPDB1`. Before step 4, type `export HOSTELHUB_DB_URL=jdbc:oracle:thin:@localhost:1521/FREEPDB1`.

## SQL scripts

The scripts in `sql/` run in SQL*Plus without changes, in this order:

| Script | Run as | Purpose |
| --- | --- | --- |
| `00_create_user.sql` | SYSTEM | Creates the `hostelhub` user with the minimum grants |
| `01_schema.sql` | hostelhub | Creates the tables and the named constraints |
| `02_views.sql` | hostelhub | Creates `V_ROOM_OCCUPANCY` and `V_OVERDUE_COMPLAINTS` |
| `03_triggers.sql` | hostelhub | Creates the four triggers |
| `04_seed.sql` | hostelhub | Adds the sample data |
| `05_queries.sql` | hostelhub | Shows joins, totals and views, then 17 rule violations with their Oracle errors |
| `99_drop.sql` | hostelhub | Removes all objects. You can run it again safely |

## Connection settings

`src/main/resources/db.properties` holds the default connection (`localhost:1521/XEPDB1`, user `hostelhub`, password `hostelhub`). These environment variables replace the defaults: `HOSTELHUB_DB_URL`, `HOSTELHUB_DB_USER` and `HOSTELHUB_DB_PASSWORD`.

## Tests

The tests use a real Oracle database. Testcontainers starts it in Docker, so Docker must be open.

```
./mvnw verify                                              # Windows (.\mvnw.cmd) and macOS
xvfb-run -a -s "-screen 0 1280x800x24" ./mvnw verify       # Linux
./mvnw verify -DexcludedGroups=gui                         # skip the GUI tests
./mvnw verify -Dhostelhub.oracle.image=gvenzl/oracle-free:23-slim-faststart   # Apple chip Macs
```

| Package | What the tests prove |
| --- | --- |
| `db` | Each of the 87 constraints rejects bad data with the exact Oracle error. The triggers and views work. The schema agrees with the PRD. The scripts run in real SQL*Plus, also after `99_drop.sql`. |
| `service` | Each FR works. Each BR is rejected by the service layer and by the database (`BusinessRulesTest`). A failed transfer rolls back. Of 4 threads that try to take the last bed, only 1 succeeds. |
| `util` | Password hashes and the Oracle error messages. |
| `ui` | 10 GUI flows with AssertJ Swing. Each flow saves a screenshot to `target/screenshots/`. |

The last full run passed 199 of 199 tests on Oracle XE 21c. The 189 non-GUI tests also pass on Oracle Database 23ai Free. JaCoCo writes the coverage report to `target/site/jacoco/index.html`.

To make `docs/TEST_CASES.md` again from the last test run, type `python3 docs/gen_test_cases.py`.

To take new screenshots of each screen from the running app (Linux, with the database from the quick start):

```
MAVEN_OPTS="--add-opens java.base/java.util=ALL-UNNAMED" xvfb-run -a -s "-screen 0 1280x800x24" ./mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.hostelhub.ui.ScreenshotTour -Dexec.args=docs/screenshots
```

## Project structure

```
sql/                       SQL*Plus scripts 00 to 05 and 99
src/main/java/com/hostelhub/
  App.java                 starts the app
  config/, db/             connection settings, connections, script runner
  model/                   records and enums
  dao/                     one DAO per table; the only code with SQL (PreparedStatement only)
  service/                 business rules and transactions
  ui/                      Swing windows, panels and dialogs (no JDBC)
  util/                    password hashes, Oracle error messages, session
src/test/java/com/hostelhub/   db/, service/, util/ and ui/ tests
docs/                      guides, test cases and screenshots
.github/workflows/ci.yml   runs all tests on each push
```

The layers are: Swing UI, then services, then DAOs (JDBC `PreparedStatement`), then `ojdbc11`, then Oracle. The UI does each database call in a background thread (`SwingWorker`), so the window does not freeze.

## License

MIT. See [LICENSE](LICENSE).
