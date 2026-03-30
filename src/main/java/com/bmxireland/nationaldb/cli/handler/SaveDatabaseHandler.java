package com.bmxireland.nationaldb.cli.handler;

import java.io.IOException;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.service.DatabaseService;

/**
 * Writes all in-memory changes to a new timestamped xlsx file.
 */
@Component
public class SaveDatabaseHandler implements MenuHandler {

    private static final Logger log = LoggerFactory.getLogger(SaveDatabaseHandler.class);

    private final DatabaseService databaseService;
    private final SessionState session;

    public SaveDatabaseHandler(DatabaseService databaseService, SessionState session) {
        this.databaseService = databaseService;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        save();
    }

    /**
     * Performs the save and updates session state. Can be called directly by other handlers
     * (e.g. exit confirmation) without needing a Scanner.
     */
    public void save() {
        try {
            String savedPath = databaseService.saveDatabase(session.getMembers());
            session.clearChanged();
            System.out.println("\nDatabase saved to: " + savedPath);
        } catch (IOException e) {
            System.err.println("\nERROR: Could not save database: " + e.getMessage());
            log.error("Failed to save database", e);
        }
    }
}
