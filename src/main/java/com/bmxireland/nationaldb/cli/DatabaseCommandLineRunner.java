package com.bmxireland.nationaldb.cli;

import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.DatabaseService;
import com.bmxireland.nationaldb.service.ValidationService;
import com.bmxireland.nationaldb.service.ValidationService.ValidationIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * Command-line interface for the BMX Ireland National Database application.
 * Loads the database on startup, runs validations, and presents an interactive menu.
 */
@Component
public class DatabaseCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCommandLineRunner.class);

    private final DatabaseService databaseService;
    private final ValidationService validationService;

    private List<Member> members;
    private boolean unsavedChanges = false;

    public DatabaseCommandLineRunner(DatabaseService databaseService, ValidationService validationService) {
        this.databaseService = databaseService;
        this.validationService = validationService;
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        printBanner();

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
                case "5" -> saveDatabase();
                case "6" -> running = confirmExit(scanner);
                default -> System.out.println("Invalid option. Please enter 1-6.");
            }
        }

        System.out.println("\nGoodbye.");
    }

    // ---- Menu display ----

    private void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     BMX Ireland - National Database Manager      ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
    }

    private void printMainMenu() {
        System.out.println();
        System.out.println("═══════════════ Main Menu ═══════════════");
        System.out.println("  1. Search / View Member");
        System.out.println("  2. Update Member");
        System.out.println("  3. Re-run Validation");
        System.out.println("  4. List All Members");
        System.out.println("  5. Save Database");
        System.out.println("  6. Exit");
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

        List<Member> results = searchMembers(query);
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

        List<Member> results = searchMembers(query);
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

            String fieldName = fieldNames.get(fieldIdx);
            String currentValue = databaseService.getFieldValue(target, fieldName);
            System.out.printf("Current value of '%s': %s%n", fieldName,
                    currentValue != null ? currentValue : "(empty)");
            System.out.printf("Enter new value for '%s' (or Enter to cancel): ", fieldName);
            String newValue = scanner.nextLine().trim();

            if (newValue.isEmpty()) {
                System.out.println("Update cancelled.");
                return;
            }

            // Apply the update
            String oldValue = currentValue != null ? currentValue : "(empty)";
            if (databaseService.updateMemberField(target, fieldName, newValue)) {
                System.out.printf("Updated '%s' for %s %s: '%s' -> '%s'%n",
                        fieldName, target.getGivenName(), target.getFamilyName(),
                        oldValue, newValue);
                unsavedChanges = true;

                // Re-validate after update
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
        int pageSize = 20;
        int totalPages = (members.size() + pageSize - 1) / pageSize;
        int currentPage = 0;

        while (true) {
            int start = currentPage * pageSize;
            int end = Math.min(start + pageSize, members.size());

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
                case "Q", "" -> {
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    private void saveDatabase() {
        try {
            databaseService.saveDatabase(members);
            unsavedChanges = false;
            System.out.println("\nDatabase saved successfully. A backup of the previous version was also created.");
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

    // ---- Search helper ----

    private List<Member> searchMembers(String query) {
        String lowerQuery = query.toLowerCase();
        return members.stream()
                .filter(m -> {
                    String licNum = m.getLicenseNumber() != null ? m.getLicenseNumber().toLowerCase() : "";
                    String given = m.getGivenName() != null ? m.getGivenName().toLowerCase() : "";
                    String family = m.getFamilyName() != null ? m.getFamilyName().toLowerCase() : "";
                    String club = m.getClubName() != null ? m.getClubName().toLowerCase() : "";

                    return licNum.contains(lowerQuery)
                            || given.contains(lowerQuery)
                            || family.contains(lowerQuery)
                            || club.contains(lowerQuery)
                            || (given + " " + family).contains(lowerQuery);
                })
                .collect(Collectors.toList());
    }
}
