package com.bmxireland.nationaldb.cli.handler;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.model.RegistrationEntry;
import com.bmxireland.nationaldb.service.DatabaseService;
import com.bmxireland.nationaldb.service.MemberService;
import com.bmxireland.nationaldb.service.MemberService.ImportResult;

/**
 * Loads a Cycling Ireland registration export file and merges it into the member database.
 */
@Component
public class ImportRegistrationHandler implements MenuHandler {

    private static final Logger log = LoggerFactory.getLogger(ImportRegistrationHandler.class);

    private final DatabaseService databaseService;
    private final MemberService memberService;
    private final ValidationHandler validationHandler;
    private final SessionState session;

    public ImportRegistrationHandler(DatabaseService databaseService, MemberService memberService,
                                     ValidationHandler validationHandler, SessionState session) {
        this.databaseService = databaseService;
        this.memberService = memberService;
        this.validationHandler = validationHandler;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
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

        ImportResult result = memberService.importRegistrationData(session.getMembers(), entries);

        System.out.println("\n  ── Updated (existing members) ──");
        if (result.updated().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (ImportResult.UpdatedEntry u : result.updated()) {
                System.out.printf("    %-25s  licence: %-12s -> %-12s  (matched by %s)%n",
                        u.member().getGivenName() + " " + u.member().getFamilyName(),
                        u.oldLicenseNumber() != null ? u.oldLicenseNumber() : "(empty)",
                        u.newLicenseNumber(), u.matchMethod());
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
                log.warn("Import skipped: {} {} [{}] — {}",
                        s.firstName(), s.lastName(), s.licenseNumber(), s.reason());
            }
        }

        System.out.printf("%n  Done. %d updated, %d added, %d skipped.%n",
                result.updated().size(), result.added().size(), result.skipped().size());

        if (!result.updated().isEmpty() || !result.added().isEmpty()) {
            session.markChanged();
            System.out.println("\nRe-validating...");
            validationHandler.runInline();
        }
        System.out.println("═════════════════════════════════════════════════════");
    }
}
