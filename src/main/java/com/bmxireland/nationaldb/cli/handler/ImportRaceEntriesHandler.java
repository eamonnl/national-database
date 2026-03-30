package com.bmxireland.nationaldb.cli.handler;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.BookingEntry;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.EventMasterService;
import com.bmxireland.nationaldb.service.EventMasterService.BookingImportResult;
import com.bmxireland.nationaldb.service.EventMasterService.SqorzExportResult;

/**
 * Imports an EventMaster BookingDetails report:
 * Phase 1 — merges new/updated members into the database.
 * Phase 2 — generates a Sqorz timing-system entry CSV.
 */
@Component
public class ImportRaceEntriesHandler implements MenuHandler {

    private static final Logger log = LoggerFactory.getLogger(ImportRaceEntriesHandler.class);

    private final EventMasterService eventMasterService;
    private final ValidationHandler validationHandler;
    private final SessionState session;

    public ImportRaceEntriesHandler(EventMasterService eventMasterService,
                                    ValidationHandler validationHandler,
                                    SessionState session) {
        this.eventMasterService = eventMasterService;
        this.validationHandler = validationHandler;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.println("\n═══════════════ Import Race Entries (EventMaster) ═══════════════");
        System.out.print("  Enter path to EventMaster BookingDetails xlsx file: ");
        String filePath = scanner.nextLine().trim();
        if (filePath.isEmpty()) {
            System.out.println("Cancelled.");
            return;
        }

        List<BookingEntry> entries;
        try {
            entries = eventMasterService.loadBookingFile(filePath);
        } catch (IOException e) {
            System.err.println("  ERROR: Could not read file: " + e.getMessage());
            log.error("Failed to load booking file: {}", filePath, e);
            return;
        }

        System.out.printf("  Loaded %d booking entries.%n", entries.size());

        // Phase 1: merge into member database
        BookingImportResult importResult = eventMasterService.importBookings(session.getMembers(), entries);

        System.out.println("\n  ── New members added ──");
        if (importResult.added().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (Member m : importResult.added()) {
                System.out.printf("    %-25s  licence: %s%n",
                        m.getGivenName() + " " + m.getFamilyName(), m.getLicenseNumber());
            }
        }

        System.out.println("\n  ── Existing members updated (plate / transponder backfill) ──");
        if (importResult.updated().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (BookingImportResult.UpdatedMember u : importResult.updated()) {
                Member m = u.member();
                String plateInfo       = u.updatedPlate()       != null ? "plate=" + u.updatedPlate()       : "";
                String transponderInfo = u.updatedTransponder() != null ? "transponder=" + u.updatedTransponder() : "";
                String changes = String.join(", ",
                        Stream.of(plateInfo, transponderInfo).filter(s -> !s.isEmpty()).toList());
                System.out.printf("    %-25s  [%s]  %s%n",
                        m.getGivenName() + " " + m.getFamilyName(), m.getLicenseNumber(), changes);
            }
        }

        System.out.printf("%n  Done. %d added, %d updated.%n",
                importResult.added().size(), importResult.updated().size());

        if (!importResult.added().isEmpty() || !importResult.updated().isEmpty()) {
            session.markChanged();
            System.out.println("\nRe-validating...");
            validationHandler.runInline();
        }

        // Phase 2: generate Sqorz CSV
        System.out.println("\n  ── Generating Sqorz entry file ──");
        try {
            SqorzExportResult exportResult = eventMasterService.generateSqorzCsv(session.getMembers(), entries);

            System.out.printf("  Sqorz CSV written to: %s (%d entries)%n",
                    exportResult.outputPath(), exportResult.entriesWritten());

            if (!exportResult.classMismatches().isEmpty()) {
                System.out.printf("%n  WARNING: %d class mismatch(es) detected — DOB-derived class used:%n",
                        exportResult.classMismatches().size());
                for (SqorzExportResult.ClassMismatchWarning w : exportResult.classMismatches()) {
                    System.out.printf("    %-25s  [%s]  EventMaster: %-15s  DOB-derived: %s%n",
                            w.name(), w.licenseNumber(), w.eventMasterClass(), w.dobDerivedClass());
                }
            }
        } catch (IOException e) {
            System.err.println("  ERROR: Could not write Sqorz CSV: " + e.getMessage());
            log.error("Failed to generate Sqorz CSV", e);
        }

        System.out.println("═════════════════════════════════════════════════════════════════");
    }
}
