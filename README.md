# BMX Ireland — National Database Manager

A command-line tool for managing the BMX Ireland national member database. It is used by the national administrator to keep member records accurate between annual licensing cycles and race events.

Key capabilities:

- **Import registrations** — load a Cycling Ireland export file and automatically match entries to existing members (by MID, licence number, or name + date of birth). New members are created; returning members have their licence number and details updated.
- **Assign race numbers** — bulk-assign Plate 20 numbers from a pasted list, and view which numbers are unassigned or reclaimable from lapsed members.
- **Validate data quality** — detect duplicate plate and transponder numbers, possible duplicate member registrations, out-of-range race numbers, incorrect date formats, transponder format errors, and licence expiry mismatches.
- **Edit records** — search by name, licence number, or club; update any field for a selected member.
- **Save with formatting** — write the full in-memory database back to a new timestamped `.xlsx` file that preserves all original tabs, styles, and column widths. Stale-licence rows are highlighted amber. Family names are normalised to uppercase.

## Requirements

- Java 21+
- Maven 3.6+
- `MemberDatabase.xlsx` in the project root directory

## Running the application

```bash
mvn spring-boot:run
```

The database file path defaults to `MemberDatabase.xlsx` in the working directory. To use a different path:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--database.file.path=/path/to/file.xlsx
```

## Building a standalone JAR

```bash
mvn package
java -jar target/national-database-1.0.0-SNAPSHOT.jar
```

## Menu options

```
  1. Search / View Member       — Search by name, licence number, or club; view full record
  2. Update Member              — Edit any field for a selected member record
  3. Re-run Validation          — Re-check the full database for data quality issues
  4. List All Members           — Browse all members in pages of 20
  5. Bulk Update Race Numbers   — Paste a list of name/number pairs to set Plate 20 in bulk
  6. Available Race Numbers     — List unassigned Plate 20 numbers and reclaimable stale assignments
  7. Import Registration File   — Load a Cycling Ireland export and merge into the database
  8. Import Race Entries        — Load an EventMaster BookingDetails export and merge into the database; generate Sqorz timing CSV
  9. Save Database              — Write all changes to a new timestamped .xlsx file
 10. Exit
```

## Validation

Validation runs automatically on startup, after any field update, and after a bulk import. It can also be triggered manually from the menu. The following checks are performed:

| Check | Description |
|-------|-------------|
| **Duplicate plate numbers** | No two members may share the same race number within a category (Plate 20, 24, Retro, Open) |
| **Duplicate transponder numbers** | No two members may share the same transponder ID within a category |
| **Invalid race number range** | Plate numbers must be three digits or fewer (1–999) |
| **Possible duplicate members** | Members with the same name (within 2 characters) and date of birth (within 5 days) who lack distinct international IDs are flagged as possible duplicate registrations |
| **Licence expiry mismatch** | Licences whose number begins with a two-digit year (e.g. `23U`, `25S`) must expire on 31 December of that year |
| **Invalid date format** | Birth Date and Licence Expiry must be in `YYYY-MM-DD` format |
| **Invalid transponder format** | Transponder numbers must match `AA-NNNNN` (two uppercase letters, hyphen, five digits) |

Issues are displayed with the affected member records identified by name and licence number.

## Bulk race number update

Option 5 accepts a paste of name/number pairs, one per line. Two formats are supported:

```
First Last  #342
First Last  = 342
```

Names are matched against the database using a flexible search (substring and normalised-space matching). Lines with no match, or where the name matches more than one member, are reported as skipped with a reason.

## Importing a registration file

Option 7 reads a Cycling Ireland `.xlsx` export. The file must contain exactly one sheet.

Each entry is matched to the existing database in priority order:

1. **UCIID / MID** (Cycling Ireland member ID) — most reliable; survives annual licence changes
2. **Licence number** — exact match on the current licence string
3. **Name + date of birth** — fallback for members who were previously added without a UCIID, or whose licence number has changed. Name matching strips all whitespace, punctuation, and capitalisation differences (e.g. `O'Brien`, `Obrien`, and `O BRIEN` all match). DOB is compared with a ±5-day tolerance to absorb common data entry errors.

Matched members have their licence number, licence expiry, and active status updated. When a member is matched by name + DOB and their database record has no UCIID, the UCIID from the import file is written back — so future imports find them by UCIID instead of falling back to name + DOB again.

Unmatched entries are added as new members.

Emergency contact details, race plate numbers, transponder numbers, and other member-managed fields are **never overwritten** by an import — only licence data is updated.

### Configuring the column format

The Cycling Ireland export format can change from year to year. Column names are configured in `src/main/resources/application.yml` under `ci-registration.format` — no code change or rebuild is needed when CI rename columns:

```yaml
ci-registration:
  format:
    club: "Club"
    expiry-date: "Membership Year"   # or "Expiry Date" if a full date column is present
    license-number: "License Number"
    member-id: "UCIID"
    # ... etc.
```

Header matching is case-insensitive and ignores leading/trailing whitespace. Set a field to `""` to mark it as absent in the current year's file — it will be skipped during validation and left null in the imported record.

When the expiry column contains a bare 4-digit year (e.g. `2026` from a "Membership Year" column), the licence expiry is automatically set to 31 December of that year.

## Importing race entries (EventMaster)

Option 8 reads a **BookingDetails** export from the EventMaster event-management platform. It has two responsibilities: merging race entries into the member database, and generating a ready-to-load CSV for the Sqorz timing system.

### How to use

1. From EventMaster, export the event's booking details as an `.xlsx` file.
2. Select **option 8** from the menu and enter the path to that file when prompted.
3. After the member import summary is shown, choose the division type:
   - **National Series** — age-based divisions derived from the EventMaster age group and cross-checked against date of birth.
   - **Club Event** — ability-based divisions read from a separate division spreadsheet. You will be prompted for the path to that file.
4. The tool writes a Sqorz CSV to the current directory and reports any warnings.

**National Series example:**

```
═══════════════ Import Race Entries (EventMaster) ═══════════════
  Enter path to EventMaster BookingDetails xlsx file: /path/to/BookingDetails.xlsx

  Loaded 42 booking entries.
  ...
  Done. 1 added, 1 updated.

  Division type:
    1. National Series  (age-based divisions)
    2. Club Event       (ability-based divisions)
  Select: 1
  Using age-based divisions.

  Sqorz CSV written to: SqorzEntries_20260424_143022.csv (42 entries)

  WARNING: 1 class mismatch(es) detected — DOB-derived class used:
    Jane Smith        [25S-1234]  EventMaster: Male 15+    DOB-derived: Male 13-14
```

**Club Event example:**

```
  Division type:
    1. National Series  (age-based divisions)
    2. Club Event       (ability-based divisions)
  Select: 2
  Enter path to ability-based division xlsx file: /path/to/Divisions.xlsx
  Loaded 147 riders from division file.

  Sqorz CSV written to: SqorzEntries_20260424_143022.csv (42 entries)

  WARNING: 2 rider(s) not found in division file — assigned D0 — Unassigned (manual assignment required):
    John Doe
    Mary Murphy
```

### Algorithm

The import runs in two sequential phases.

#### Phase 1 — Merge bookings into the member database

1. **Parse the file.** The `.xlsx` file must have a header row (row 0) followed by one row per entry. Column order does not matter — columns are discovered by name. Rows with a blank CI Licence Number are skipped.
2. **Match against the database.** Entries are matched by licence number (case-insensitive). Entries with no match are added as new members with `Active = Yes`.
3. **Backfill blank fields.** For existing members the database is authoritative. Only two fields are backfilled from the booking entry, and only if the database field is currently blank:
   - **Plate 20** — accepted if the booking value is an integer between 1 and 999.
   - **Transponder 20** — accepted if the booking value matches `AA-NNNNN` (two uppercase letters, a hyphen, five digits).
   - All other fields — licence data, emergency contacts, email, and team affiliations — are never overwritten.

#### Phase 2 — Generate the Sqorz timing CSV

After the member import summary, the user is prompted to choose the division type. A CSV is then written to the working directory with a timestamped filename (`SqorzEntries_YYYYMMDD_HHmmss.csv`). The file uses **Mac Roman encoding**, which is required by Sqorz. The class written to the CSV is always the **full division name** (e.g. `Mini Shredders`), not the code.

For each booking entry:

1. **Derive the race class** — see the two strategies below.
2. **Resolve plate and transponder.** The member database record is used in preference to the booking entry; the booking entry is used as a fallback if the database field is blank.
3. **Resolve the club.** The member's club name is matched against the canonical Sqorz club list (exact match, then substring match, then `Other`).

**National Series strategy (age-based)**

EventMaster age groups are mapped to Sqorz class names using the table below. If the EventMaster value is not in the table, the class is derived from the member's date of birth using calendar-year age. When the two methods disagree, the DOB-derived class is used and a warning is printed.

| EventMaster age group | Sqorz class |
|---|---|
| 6 & Under (Mixed Male & Female) | Under 6 Mixed |
| Male 7-8 | Male 7-8 |
| Male 9-10 | Male 9-10 |
| Male 11-12 | Male 11-12 |
| Male 13-14 | Male 13-14 |
| Male 15+ | Male 15-29 |
| Female 7-10 | Female 7-10 |
| Female 11-14 | Female 11-14 |
| Female 15+ | Female 15-29 |
| Male 30+ Open | Masters 30+ |
| Female 30+ open | Female 30+ |
| Superclass | SuperClass |

When an age group is absent or unrecognised, the class is derived from date of birth: males map to 7-8, 9-10, 11-12, 13-14, 15-29, or 30+; females to 7-10, 11-14, 15-29, or 30+; age ≤ 6 gives Under 6 Mixed regardless of gender.

**Club Event strategy (ability-based)**

The user supplies an ability-based division spreadsheet (see format below). Each rider's name from the EventMaster booking is looked up in that file to find their assigned division. The EventMaster name is tried first; if not found, the member database name is tried. Names are matched by stripping all non-alphanumeric characters and comparing case-insensitively, so `ReubenBYRNE` and `Reuben Byrne` are treated as the same name.

Riders not found in the division file are assigned **Unassigned (D0)** and listed as warnings requiring manual correction before the CSV is loaded into Sqorz.

**Club divisions**

| Code | Name |
|------|------|
| D0 | Unassigned |
| D1 | Airborne Aces |
| D2 | Corner Commanders |
| D3 | Chain Breakers |
| D4 | Gate Crushers |
| D5 | Intermaniacs |
| D6 | Speed Squad |
| D7 | Mini Shredders |
| M1 | Rhythm Masters |

**Sqorz club list**

| Club | Country |
|------|---------|
| Belfast City BMX | IRL |
| Cork BMX | IRL |
| Courtown BMX | IRL |
| East Coast Raiders BMX | IRL |
| Lisburn BMX | UK |
| Lucan BMX | IRL |
| Ratoath BMX | IRL |
| Saint Annes BMX | IRL |
| Other (fallback) | IRL |

### Input file formats

**EventMaster BookingDetails** (`.xlsx`)

The file must contain a header row followed by data rows. The following column names are expected (case-insensitive, leading/trailing whitespace ignored):

| Column | Required | Notes |
|--------|----------|-------|
| CI Licence Number | Yes | Rows with a blank value are skipped |
| First Name, Last Name | Yes | |
| Gender | Yes | Normalised to `M` or `F` |
| Date of Birth | Yes | Accepts `d/M/yyyy` or `YYYY-MM-DD` |
| CI mid | No | Cycling Ireland member ID |
| CI Club | No | |
| CI Rider Category | No | Used to derive `License Class` (Adult / Youth) |
| Age Group Youth, Age Group Adult | No | Used for Sqorz class derivation (National Series) |
| BMX Race Number | No | Must be an integer 1–999; invalid values are ignored |
| Transponder Number | No | Must match `AA-NNNNN`; invalid values are ignored |
| Emergency Contact Name, Emergency Contact Phone | No | |
| Email, Mobile | No | |

**Ability-based division file** (`.xlsx`, Club Event only)

The file must contain a **Riders** sheet at sheet index 1 with a header row (row 0) followed by one row per rider. Only two columns are read:

| Column index | Content |
|---|---|
| 0 | Rider name (first and last name, typically concatenated) |
| 5 | Division code (e.g. `D3`, `M1`) |

## Saving

Each save writes a new file with a timestamp suffix, leaving the original untouched:

```
MemberDatabase_20260326_171500.xlsx
```

The saved file is a modified copy of the original workbook, so all tabs, column widths, cell styles, and validation sheets are preserved. Additionally:

- Members are sorted alphabetically by family name
- Family names are written in UPPERCASE with name prefixes separated (e.g. `MC CANN`, `O FLAHERTY`)
- Rows for members whose licence expired 3 or more years ago are highlighted in amber and their Active field is set to `No`
- Phone numbers are normalised to `DDD DDDDDDD` format; international prefixes (+353, 00353) are stripped
- Club names are matched against the Groups sheet; unrecognised clubs fall back to `Other`

## Database file format

The tool expects a specific `.xlsx` structure:

1. A header section delimited by two rows whose first cell contains `****************`
2. A column header row immediately after the second delimiter
3. Member data rows following the column header row

The 28 data columns, in order:

| # | Column | Description |
|---|--------|-------------|
| 1 | License Number | Unique member identifier |
| 2 | License Class | `Adult` or `Youth` |
| 3 | License Expiry | Expiry date (`YYYY-MM-DD`) |
| 4 | Given Name | First name |
| 5 | Family Name | Surname (written in UPPERCASE on save) |
| 6 | Birth Date | Date of birth (`YYYY-MM-DD`) |
| 7 | Gender | `M` or `F` |
| 8 | Active | `Yes` or `No` |
| 9–12 | Plate 20 / 24 / Retro / Open | Race plate numbers by category (max 999) |
| 13–16 | Transponder 20 / 24 / Retro / Open | Transponder IDs (`AA-NNNNN`) by category |
| 17 | License Country Code | Country code for international licences |
| 18 | International License | International licence reference |
| 19 | Club Name | Member's club (matched against Groups sheet) |
| 20–24 | Team Name 1–5 | Team affiliations |
| 25 | Email | Primary contact email |
| 26 | CC Email | CC email address |
| 27 | Emergency Contact Person | Emergency contact name |
| 28 | Emergency Contact Number | Emergency contact phone (`DDD DDDDDDD`) |

## Project structure

```
src/main/java/com/bmxireland/nationaldb/
├── NationalDatabaseApplication.java   — Spring Boot entry point
├── cli/
│   └── DatabaseCommandLineRunner.java — Interactive menu loop
├── config/
│   └── RegistrationFormatConfig.java  — CI import column-name configuration (@ConfigurationProperties)
├── model/
│   ├── BookingEntry.java              — EventMaster booking record
│   ├── ClubDivision.java              — Ability-based club divisions enum (D0–M1)
│   ├── Member.java                    — Member record model
│   ├── RegistrationEntry.java         — Cycling Ireland import record
│   └── SqorzClub.java                 — Sqorz timing-system club record
└── service/
    ├── AbilityDivisionLoader.java     — Loads rider → division map from ability-based division xlsx
    ├── AgeBasedClassStrategy.java     — National Series: derives class from EventMaster age group / DOB
    ├── DatabaseService.java           — xlsx read/write logic
    ├── DivisionLookupClassStrategy.java — Club Event: looks up each rider's division by name
    ├── EventMasterService.java        — EventMaster import and Sqorz CSV generation
    ├── MemberService.java             — search, import, bulk update, available numbers
    ├── RaceClassStrategy.java         — Strategy interface for Sqorz class resolution
    └── ValidationService.java         — data quality checks
```
