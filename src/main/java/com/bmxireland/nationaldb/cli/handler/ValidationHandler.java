package com.bmxireland.nationaldb.cli.handler;

import java.util.List;
import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.service.ValidationService;
import com.bmxireland.nationaldb.service.ValidationService.ReclaimedPlate;
import com.bmxireland.nationaldb.service.ValidationService.ValidationIssue;
import com.bmxireland.nationaldb.service.ValidationService.ValidationResult;

/**
 * Runs all data-quality validations against the in-memory member list and prints
 * the results. Can be invoked as a menu item or called directly by other handlers
 * after a change that may affect validation.
 */
@Component
public class ValidationHandler implements MenuHandler {

    private final ValidationService validationService;
    private final SessionState session;

    public ValidationHandler(ValidationService validationService, SessionState session) {
        this.validationService = validationService;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.println("\n═══════════════ Validation Report ═══════════════");
        ValidationResult result = validationService.validateAll(session.getMembers());
        printResult(result);
        System.out.println("═════════════════════════════════════════════════");

        if (!result.reclaimed().isEmpty()) {
            session.markChanged();
        }
    }

    /**
     * Runs validation and prints a compact inline summary (used after a change).
     */
    public void runInline() {
        ValidationResult result = validationService.validateAll(session.getMembers());
        List<ValidationIssue> issues = result.issues();
        List<ReclaimedPlate> reclaimed = result.reclaimed();

        if (!reclaimed.isEmpty()) {
            session.markChanged();
        }

        if (issues.isEmpty() && reclaimed.isEmpty()) {
            System.out.println("Validation passed.");
        } else {
            if (!reclaimed.isEmpty()) {
                System.out.printf("Auto-reclaimed %d plate(s):%n", reclaimed.size());
                for (ReclaimedPlate rp : reclaimed) {
                    System.out.printf("  [%s] '%s' reclaimed from %s (stale) → kept for %s%n",
                            rp.fieldName(), rp.plateValue(),
                            rp.staleMember().toSummary(),
                            rp.activeHolder().toSummary());
                }
            }
            if (!issues.isEmpty()) {
                System.out.printf("WARNING: %d validation issue(s) after change:%n", issues.size());
                for (ValidationIssue issue : issues) {
                    System.out.print(issue.format());
                }
            }
        }
    }

    private void printResult(ValidationResult result) {
        List<ValidationIssue> issues = result.issues();
        List<ReclaimedPlate> reclaimed = result.reclaimed();

        if (!reclaimed.isEmpty()) {
            System.out.printf("  Auto-reclaimed %d plate(s):%n", reclaimed.size());
            for (ReclaimedPlate rp : reclaimed) {
                System.out.printf("    [%s] '%s' reclaimed from %s (stale) → kept for %s%n",
                        rp.fieldName(), rp.plateValue(),
                        rp.staleMember().toSummary(),
                        rp.activeHolder().toSummary());
            }
            System.out.println();
        }

        if (issues.isEmpty() && reclaimed.isEmpty()) {
            System.out.println("  All validations passed. No issues found.");
        } else if (issues.isEmpty()) {
            System.out.println("  All other validations passed.");
        } else {
            System.out.printf("  Found %d validation issue(s):%n%n", issues.size());
            for (ValidationIssue issue : issues) {
                System.out.print(issue.format());
            }
        }
    }
}
