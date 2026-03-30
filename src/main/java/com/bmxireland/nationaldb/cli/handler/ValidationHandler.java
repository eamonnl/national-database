package com.bmxireland.nationaldb.cli.handler;

import java.util.List;
import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.service.ValidationService;
import com.bmxireland.nationaldb.service.ValidationService.ValidationIssue;

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
        List<ValidationIssue> issues = validationService.validateAll(session.getMembers());

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

    /**
     * Runs validation and prints a compact inline summary (used after a change).
     */
    public void runInline() {
        List<ValidationIssue> issues = validationService.validateAll(session.getMembers());
        if (issues.isEmpty()) {
            System.out.println("Validation passed.");
        } else {
            System.out.printf("WARNING: %d validation issue(s) after change:%n", issues.size());
            for (ValidationIssue issue : issues) {
                System.out.print(issue.format());
            }
        }
    }
}
