package com.bmxireland.nationaldb.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.model.RegistrationEntry;
import com.bmxireland.nationaldb.service.DatabaseService;
import com.bmxireland.nationaldb.service.MemberService;
import com.bmxireland.nationaldb.service.MemberService.AvailableNumbersResult;
import com.bmxireland.nationaldb.service.MemberService.BulkUpdateResult;
import com.bmxireland.nationaldb.service.MemberService.ImportResult;
import com.bmxireland.nationaldb.service.ValidationService;
import com.bmxireland.nationaldb.service.ValidationService.ValidationIssue;

/**
 * Command-line interface for the BMX Ireland National Database application.
 * Loads the database on startup, runs validations, and presents an interactive
 * menu. All business logic is delegated to the service layer so it can be
 * reused by other presentation layers (e.g. a future web UI).
 */
@Component
public class DatabaseCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCommandLineRunner.class);

    private final DatabaseService databaseService;
    private final ValidationService validationService;
    private final MemberService memberService;

    private List<Member> members;
    private boolean unsavedChanges = false;

    public DatabaseCommandLineRunner(DatabaseService databaseService,
                                     ValidationService validationService,
                                     MemberService memberService) {
        this.databaseService = databaseService;
        this.validationService = validationService;
        this.memberService = memberService;
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        // Load database
        try {
            members = databaseService.loadDatabase();
        } catch (IOException e) {
            System.err.println("\nFATAL: Could not load database: " + e.getMessage());
            log.error("Failed to load database", e);
            return;
        }

        System.out.printf("\nLoaded %d members from database.%n", members.size());

        // Run validations
        runValidation();

        // Main menu loop
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> searchAndViewMember(scanner);
                case "2" -> updateMember(scanner);
                case "3" -> runValidation();
                case "4" -> listAllMembers(scanner);
                case "5" -> bulkUpdateRaceNumbers(scanner);
                case "6" -> listAvailableRaceNumbers(scanner);
                case "7" -> importRegistrationFile(scanner);
                case "8" -> saveDatabase();
                case "9" -> running = confirmExit(scanner);
                default -> System.out.println("Invalid option. Please enter 1-9.");
            }
        }

        System.out.println("\nGoodbye.");
    }

    // ---- Menu display ----

    private void printMainMenu() {
        System.out.println();
        System.out.println("═══════════════ Main Menu ═══════════════");
        System.out.println("  1. Search / View Member");
        System.out.println("  2. Update Member");
        System.out.println("  3. Re-run Validation");
        System.out.println("  4. List All Members");
        System.out.println("  5. Bulk Update Race Numbers (Plate 20)");
        System.out.println("  6. Available Race Numbers");
        System.out.println("  7. Import Registration File");
        System.out.println("  8. Save Database");
        System.out.println("  9. Exit");
        if (unsavedChanges) {
            System.out.println("  ** Unsaved changes pending **");
        }
        System.out.println("═════════════════════════════════════════");
        System.out.print("Select option: ");
    }

    // ---- Menu actions ----

    private void searchAndViewMember(Scanner scanner) {
        System.out.print("\nSearch by name or license number: ");
        String query = scanner.nextLine().trim();
        if (query.isEmpty()) return;

        List<Member> results = memberService.search(members, query);
        if (results.isEmpty()) {
            System.out.println("No members found matching: " + query);
            return;
        }

        if (results.size() == 1) {
            System.out.println("\n" + results.get(0).toDetailString());
            return;
        }

        System.out.printf("\nFound %d members:%n", results.size());
        for (int i = 0; i < results.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, results.get(i).toSummary());
        }

        System.out.print("Select member number to view details (or Enter to go back): ");
        String selection = scanner.nextLine().trim();
        if (selection.isEmpty()) return;

        try {
            int idx = Integer.parseInt(selection) - 1;
            if (idx >= 0 && idx < results.size()) {
                System.out.println("\n" + results.get(idx).toDetailString());
            } else {
                System.out.println("Invalid selection.");
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    private void updateMember(Scanner scanner) {
        System.out.print("\nSearch for member to update (name or license number): ");
        String query = scanner.nextLine().trim();
        if (query.isEmpty()) return;

        List<Member> results = memberService.search(members, query);
        if (results.isEmpty()) {
            System.out.println("No members found matching: " + query);
            return;
        }

        Member target;
        if (results.size() == 1) {
            target = results.get(0);
            System.out.println("\nFound: " + target.toSummary());
        } else {
            System.out.printf("\nFound %d members:%n", results.size());
            for (int i = 0; i < results.size(); i++) {
                System.out.printf("  %d. %s%n", i + 1, results.get(i).toSummary());
            }
            System.out.print("Select member number: ");
            String selection = scanner.nextLine().trim();
            try {
                int idx = Integer.parseInt(selection) - 1;
                if (idx < 0 || idx >= results.size()) {
                    System.out.println("Invalid selection.");
                    return;
                }
                target = results.get(idx);
            } catch (NumberFormatException e) {
                System.out.println("Invalid input.");
                return;
            }
        }

        // Show current details
        System.out.println("\nCurrent details:");
        System.out.println(target.toDetailString());

        // Show field selection menu
        List<String> fieldNames = databaseService.getFieldNames();
        System.out.println("\nSelect field to update:");
        for (int i = 0; i < fieldNames.size(); i++) {
            String currentVal = databaseService.getFieldValue(target, fieldNames.get(i));
            System.out.printf("  %2d. %-25s : %s%n", i + 1, fieldNames.get(i),
                    currentVal != null ? currentVal : "");
        }
        System.out.print("Select field number (or Enter to cancel): ");
        String fieldSelection = scanner.nextLine().trim();
        if (fieldSelection.isEmpty()) return;

        try {
            int fieldIdx = Integer.parseInt(fieldSelection) - 1;
            if (fieldIdx < 0 || fieldIdx >= fieldNames.size()) {
                System.out.println("Invalid field selection.");
                return;
            }

            String fieldName    = fieldNames.get(fieldIdx);
            String currentValue = databaseService.getFieldValue(target, fieldName);
            System.out.printf("Current value of '%s': %s%n", fieldName,
                    currentValue != null ? currentValue : "(empty)");
            System.out.printf("Enter new value for '%s' (or Enter to cancel): ", fieldName);
            String newValue = scanner.nextLine().trim();

            if (newValue.isEmpty()) {
                System.out.println("Update cancelled.");
                return;
            }

            String oldValue = currentValue != null ? currentValue : "(empty)";
            if (databaseService.updateMemberField(target, fieldName, newValue)) {
                System.out.printf("Updated '%s' for %s %s: '%s' -> '%s'%n",
                        fieldName, target.getGivenName(), target.getFamilyName(), oldValue, newValue);
                unsavedChanges = true;

                System.out.println("\nRe-validating after update...");
                List<ValidationIssue> issues = validationService.validateAll(members);
                if (!issues.isEmpty()) {
                    System.out.println("\nWARNING: Validation issues detected after update:");
                    for (ValidationIssue issue : issues) {
                        System.out.print(issue.format());
                    }
                } else {
                    System.out.println("Validation passed.");
                }
            } else {
                System.out.println("Unknown field: " + fieldName);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    private void runValidation() {
        System.out.println("\n═══════════════ Validation Report ═══════════════");
        List<ValidationIssue> issues = validationService.validateAll(members);

        if (issues.isEmpty()) {
            System.out.println("  All validations passed. No issues found.");
        } else {
            System.out.printf("  Found %d validation issue(s):%n%n", issues.size());
            for (ValidationIssue issue : issues) {
                System.out.print(issue.format());
            }
        }
        System.out.println("═════════════════════════════════════════════════");
    }

    private void listAllMembers(Scanner scanner) {
        int pageSize   = 20;
        int totalPages = (members.size() + pageSize - 1) / pageSize;
        int currentPage = 0;

        while (true) {
            int start = currentPage * pageSize;
            int end   = Math.min(start + pageSize, members.size());

            System.out.printf("\n═══ Members %d-%d of %d (Page %d/%d) ═══%n",
                    start + 1, end, members.size(), currentPage + 1, totalPages);

            for (int i = start; i < end; i++) {
                System.out.printf("  %4d. %s%n", i + 1, members.get(i).toSummary());
            }

            System.out.print("\n[N]ext / [P]rev / [Q]uit listing: ");
            String cmd = scanner.nextLine().trim().toUpperCase();

            switch (cmd) {
                case "N" -> {
                    if (currentPage < totalPages - 1) currentPage++;
                    else System.out.println("Already on last page.");
                }
                case "P" -> {
                    if (currentPage > 0) currentPage--;
                    else System.out.println("Already on first page.");
                }
                case "Q", "" -> { return; }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void bulkUpdateRaceNumbers(Scanner scanner) {
        System.out.println("\n═══════════════ Bulk Update Race Numbers (Plate 20) ═══════════════");
        System.out.println("Enter entries as 'First Last  #PlateNumber', one per line.");
        System.out.println("Trailing notes are ignored, e.g. 'Adam Hosback  #342  (needs full license)'");
        System.out.println("Enter an empty line when done.");
        System.out.println("─────────────────────────────────────────────────────────────────");

        List<String> inputLines = new ArrayList<>();
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) break;
            inputLines.add(line);
        }

        BulkUpdateResult result = memberService.bulkUpdatePlate20(members, inputLines);

        System.out.println("─────────────────────────────────────────────────────────────────");

        for (BulkUpdateResult.AppliedUpdate u : result.applied()) {
            System.out.printf("  Updated %-30s Plate 20: '%s' -> '%s'%n",
                    u.member().getGivenName() + " " + u.member().getFamilyName(),
                    u.oldValue() != null ? u.oldValue() : "(empty)", u.newValue());
            log.info("Bulk update: set Plate 20 for {} {} [{}] to '{}'",
                    u.member().getGivenName(), u.member().getFamilyName(),
                    u.member().getLicenseNumber(), u.newValue());
        }

        for (BulkUpdateResult.SkippedEntry s : result.skipped()) {
            System.out.printf("  ERROR: %s — input: '%s'%n", s.reason(), s.input());
            if (!s.matches().isEmpty()) {
                for (Member m : s.matches()) {
                    System.out.printf("           %s%n", m.toSummary());
                }
            }
            log.error("Bulk update skipped: {} — input: '{}'", s.reason(), s.input());
        }

        System.out.printf("  Done. %d updated, %d skipped.%n",
                result.applied().size(), result.skipped().size());

        if (!result.applied().isEmpty()) {
            unsavedChanges = true;
            System.out.println("\nRe-validating...");
            List<ValidationIssue> issues = validationService.validateAll(members);
            if (!issues.isEmpty()) {
                System.out.println("WARNING: Validation issues detected after bulk update:");
                for (ValidationIssue issue : issues) {
                    System.out.print(issue.format());
                }
            } else {
                System.out.println("Validation passed.");
            }
        }
    }

    private void listAvailableRaceNumbers(Scanner scanner) {
        System.out.println("\n═══════════════ Available Race Numbers (101+) ═══════════════");
        System.out.println("  Select plate category:");
        System.out.println("    1. Plate 20");
        System.out.println("    2. Plate 24");
        System.out.println("    3. Plate Retro");
        System.out.println("    4. Plate Open");
        System.out.print("  Category (or Enter to cancel): ");

        String categoryChoice = scanner.nextLine().trim();
        String fieldName = switch (categoryChoice) {
            case "1" -> "Plate 20";
            case "2" -> "Plate 24";
            case "3" -> "Plate Retro";
            case "4" -> "Plate Open";
            default  -> null;
        };

        if (fieldName == null) {
            System.out.println("Cancelled.");
            return;
        }

        AvailableNumbersResult result = memberService.getAvailableRaceNumbers(members, fieldName);

        System.out.printf("%n  Category     : %s%n", result.fieldName());
        System.out.printf("  Stale cutoff : licenses expired before %s (3+ years ago)%n", result.staleCutoff());

        System.out.println("\n  ── Reclaimable (assigned to riders with stale licenses) ──");
        if (result.reclaimable().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (AvailableNumbersResult.StaleAssignment s : result.reclaimable()) {
                Member m = s.member();
                System.out.printf("    %4d  —  %s %s  [%s]  expired: %s%n",
                        s.plateNumber(),
                        m.getGivenName()  != null ? m.getGivenName()  : "",
                        m.getFamilyName() != null ? m.getFamilyName() : "",
                        m.getLicenseNumber(),
                        m.getLicenseExpiry() != null ? m.getLicenseExpiry() : "unknown");
            }
        }

        System.out.printf("%n  ── Unassigned numbers in range 101–%d ──%n", result.maxRange());
        if (result.unassigned().isEmpty()) {
            System.out.println("    None.");
        } else {
            StringBuilder row = new StringBuilder("    ");
            List<Integer> unassigned = result.unassigned();
            for (int i = 0; i < unassigned.size(); i++) {
                row.append(String.format("%4d", unassigned.get(i)));
                if ((i + 1) % 15 == 0) {
                    System.out.println(row);
                    row = new StringBuilder("    ");
                }
            }
            if (row.length() > 4) System.out.println(row);
        }

        System.out.printf("%n  Total available: %d (%d reclaimable, %d unassigned in range 101–%d)%n",
                result.totalAvailable(), result.reclaimable().size(),
                result.unassigned().size(), result.maxRange());
        System.out.println("═════════════════════════════════════════════════════════════");
    }

    private void importRegistrationFile(Scanner scanner) {
        System.out.println("\n═══════════════ Import Registration File ═══════════════");
        System.out.print("  Enter path to registration xlsx file: ");
        String filePath = scanner.nextLine().trim();
        if (filePath.isEmpty()) {
            System.out.println("Cancelled.");
            return;
        }

        List<RegistrationEntry> entries;
        try {
            entries = databaseService.loadRegistrationFile(filePath);
        } catch (IOException e) {
            System.err.println("  ERROR: Could not read file: " + e.getMessage());
            log.error("Failed to load registration file: {}", filePath, e);
            return;
        }

        System.out.printf("  Loaded %d entries from file.%n", entries.size());

        ImportResult result = memberService.importRegistrationData(members, entries);

        System.out.println("\n  ── Updated (existing members) ──");
        if (result.updated().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (ImportResult.UpdatedEntry u : result.updated()) {
                System.out.printf("    %-25s  licence: %-12s -> %-12s  (matched by %s)%n",
                        u.member().getGivenName() + " " + u.member().getFamilyName(),
                        u.oldLicenseNumber() != null ? u.oldLicenseNumber() : "(empty)",
                        u.newLicenseNumber(),
                        u.matchMethod());
            }
        }

        System.out.println("\n  ── Added (new members) ──");
        if (result.added().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (Member m : result.added()) {
                System.out.printf("    %-25s  licence: %s%n",
                        m.getGivenName() + " " + m.getFamilyName(), m.getLicenseNumber());
            }
        }

        System.out.println("\n  ── Skipped ──");
        if (result.skipped().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (ImportResult.SkippedEntry s : result.skipped()) {
                System.out.printf("    %-25s  [%s]  reason: %s%n",
                        s.firstName() + " " + s.lastName(), s.licenseNumber(), s.reason());
                log.warn("Import skipped: {} {} [{}] — {}", s.firstName(), s.lastName(),
                        s.licenseNumber(), s.reason());
            }
        }

        System.out.printf("%n  Done. %d updated, %d added, %d skipped.%n",
                result.updated().size(), result.added().size(), result.skipped().size());

        if (!result.updated().isEmpty() || !result.added().isEmpty()) {
            unsavedChanges = true;
            System.out.println("\nRe-validating...");
            var issues = validationService.validateAll(members);
            if (!issues.isEmpty()) {
                System.out.println("WARNING: Validation issues after import:");
                for (var issue : issues) System.out.print(issue.format());
            } else {
                System.out.println("Validation passed.");
            }
        }

        System.out.println("═════════════════════════════════════════════════════");
    }

    private void saveDatabase() {
        try {
            String savedPath = databaseService.saveDatabase(members);
            unsavedChanges = false;
            System.out.println("\nDatabase saved to: " + savedPath);
        } catch (IOException e) {
            System.err.println("\nERROR: Could not save database: " + e.getMessage());
            log.error("Failed to save database", e);
        }
    }

    private boolean confirmExit(Scanner scanner) {
        if (unsavedChanges) {
            System.out.print("\nYou have unsaved changes. Save before exiting? (Y/N): ");
            String answer = scanner.nextLine().trim().toUpperCase();
            if ("Y".equals(answer)) {
                saveDatabase();
            }
        }
        return false; // false = stop running
    }
}
