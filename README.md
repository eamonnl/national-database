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
  8. Save Database              — Write all changes to a new timestamped .xlsx file
  9. Exit
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
│   ├── Member.java                    — Member record model
│   └── RegistrationEntry.java         — Cycling Ireland import record
└── service/
    ├── DatabaseService.java           — xlsx read/write logic
    ├── MemberService.java             — search, import, bulk update, available numbers
    └── ValidationService.java         — data quality checks
```
