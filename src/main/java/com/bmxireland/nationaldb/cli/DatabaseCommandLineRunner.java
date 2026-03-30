package com.bmxireland.nationaldb.cli;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.handler.AvailableNumbersHandler;
import com.bmxireland.nationaldb.cli.handler.BulkUpdateHandler;
import com.bmxireland.nationaldb.cli.handler.ImportRaceEntriesHandler;
import com.bmxireland.nationaldb.cli.handler.ImportRegistrationHandler;
import com.bmxireland.nationaldb.cli.handler.ListMembersHandler;
import com.bmxireland.nationaldb.cli.handler.SaveDatabaseHandler;
import com.bmxireland.nationaldb.cli.handler.SearchHandler;
import com.bmxireland.nationaldb.cli.handler.UpdateHandler;
import com.bmxireland.nationaldb.cli.handler.ValidationHandler;
import com.bmxireland.nationaldb.service.DatabaseService;

/**
 * Entry point for the interactive CLI. Loads the database, runs initial validation,
 * then delegates every menu choice to a dedicated {@link MenuHandler}.
 */
@Component
public class DatabaseCommandLineRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCommandLineRunner.class);

    private final DatabaseService databaseService;
    private final SessionState session;
    private final ValidationHandler validationHandler;
    private final SaveDatabaseHandler saveDatabaseHandler;
    private final Map<String, MenuHandler> menuHandlers;

    public DatabaseCommandLineRunner(
            DatabaseService databaseService,
            SessionState session,
            ValidationHandler validationHandler,
            SearchHandler searchHandler,
            UpdateHandler updateHandler,
            ListMembersHandler listMembersHandler,
            BulkUpdateHandler bulkUpdateHandler,
            AvailableNumbersHandler availableNumbersHandler,
            ImportRegistrationHandler importRegistrationHandler,
            ImportRaceEntriesHandler importRaceEntriesHandler,
            SaveDatabaseHandler saveDatabaseHandler) {

        this.databaseService    = databaseService;
        this.session            = session;
        this.validationHandler  = validationHandler;
        this.saveDatabaseHandler = saveDatabaseHandler;

        menuHandlers = new LinkedHashMap<>();
        menuHandlers.put("1",  searchHandler);
        menuHandlers.put("2",  updateHandler);
        menuHandlers.put("3",  validationHandler);
        menuHandlers.put("4",  listMembersHandler);
        menuHandlers.put("5",  bulkUpdateHandler);
        menuHandlers.put("6",  availableNumbersHandler);
        menuHandlers.put("7",  importRegistrationHandler);
        menuHandlers.put("8",  importRaceEntriesHandler);
        menuHandlers.put("9",  saveDatabaseHandler);
    }

    @Override
    public void run(String... args) {
        Scanner scanner = new Scanner(System.in);

        try {
            session.setMembers(databaseService.loadDatabase());
        } catch (IOException e) {
            System.err.println("\nFATAL: Could not load database: " + e.getMessage());
            log.error("Failed to load database", e);
            return;
        }

        System.out.printf("\nLoaded %d members from database.%n", session.getMembers().size());
        validationHandler.handle(scanner);

        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = scanner.nextLine().trim();

            MenuHandler handler = menuHandlers.get(choice);
            if (handler != null) {
                handler.handle(scanner);
            } else if ("10".equals(choice)) {
                running = confirmExit(scanner);
            } else {
                System.out.println("Invalid option. Please enter 1-10.");
            }
        }

        System.out.println("\nGoodbye.");
    }

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
        System.out.println("  8. Import Race Entries (EventMaster)");
        System.out.println("  9. Save Database");
        System.out.println(" 10. Exit");
        if (session.hasUnsavedChanges()) {
            System.out.println("  ** Unsaved changes pending **");
        }
        System.out.println("═════════════════════════════════════════");
        System.out.print("Select option: ");
    }

    private boolean confirmExit(Scanner scanner) {
        if (session.hasUnsavedChanges()) {
            System.out.print("\nYou have unsaved changes. Save before exiting? (Y/N): ");
            if ("Y".equalsIgnoreCase(scanner.nextLine().trim())) {
                saveDatabaseHandler.save();
            }
        }
        return false;
    }
}
