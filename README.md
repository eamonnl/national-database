# BMX Ireland — National Database Manager

An administrative command-line tool for managing the BMX Ireland member database. It reads from and writes to an Excel (`.xlsx`) spreadsheet, runs data validation on startup, and provides an interactive menu for searching, viewing, and updating member records.

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

## Database file format

The tool expects a specific `.xlsx` structure:

1. A header section delimited by two rows whose first cell contains `****************`
2. A column header row immediately after the second delimiter
3. Member data rows following the column header row

The 28 data columns, in order:

| # | Column | Description |
|---|--------|-------------|
| 1 | License Number | Unique member identifier |
| 2 | License Class | e.g. Adult, Youth, Junior |
| 3 | License Expiry | Expiry date |
| 4 | Given Name | First name |
| 5 | Family Name | Surname |
| 6 | Birth Date | Date of birth |
| 7 | Gender | Gender |
| 8 | Active | Active status (Yes/No) |
| 9–12 | Plate 20 / 24 / Retro / Open | Race plate numbers by category |
| 13–16 | Transponder 20 / 24 / Retro / Open | Transponder IDs by category |
| 17 | License Country Code | Country code for international licenses |
| 18 | International License | International license reference |
| 19 | Club Name | Member's club |
| 20–24 | Team Name 1–5 | Team affiliations |
| 25 | Email | Primary contact email |
| 26 | CC Email | CC email address |
| 27 | Emergency Contact Person | Emergency contact name |
| 28 | Emergency Contact Number | Emergency contact phone |

## Menu options

```
  1. Search / View Member      — Search by name, license number, or club
  2. Update Member             — Edit any field for a member record
  3. Re-run Validation         — Re-check the database for issues
  4. List All Members          — Browse all members in pages of 20
  5. Save Database             — Write changes back to the .xlsx file
  6. Exit
```

## Validation

On startup (and after any update), the tool checks for:

- **Duplicate plate numbers** — no two members should share the same race plate number within a category (20", 24", Retro, Open)
- **Duplicate transponder numbers** — no two members should share the same transponder ID within a category

Issues are displayed with the affected member records identified by name and license number.

## Saving

Saving writes all changes back to the original `.xlsx` file and automatically creates a timestamped backup in the same directory before overwriting:

```
MemberDatabase_backup_20260302_163000.xlsx
```

## Project structure

```
src/main/java/com/bmxireland/nationaldb/
├── NationalDatabaseApplication.java   — Spring Boot entry point
├── cli/
│   └── DatabaseCommandLineRunner.java — Interactive menu loop
├── model/
│   └── Member.java                    — Member record model
└── service/
    ├── DatabaseService.java           — xlsx read/write logic
    └── ValidationService.java         — Duplicate detection
```
