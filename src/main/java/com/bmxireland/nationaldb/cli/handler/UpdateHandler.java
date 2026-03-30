package com.bmxireland.nationaldb.cli.handler;

import java.util.List;
import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.DatabaseService;
import com.bmxireland.nationaldb.service.MemberService;

/**
 * Handles searching for a member and updating a single field value.
 */
@Component
public class UpdateHandler implements MenuHandler {

    private final MemberService memberService;
    private final DatabaseService databaseService;
    private final ValidationHandler validationHandler;
    private final SessionState session;

    public UpdateHandler(MemberService memberService, DatabaseService databaseService,
                         ValidationHandler validationHandler, SessionState session) {
        this.memberService = memberService;
        this.databaseService = databaseService;
        this.validationHandler = validationHandler;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.print("\nSearch for member to update (name or license number): ");
        String query = scanner.nextLine().trim();
        if (query.isEmpty()) return;

        List<Member> results = memberService.search(session.getMembers(), query);
        if (results.isEmpty()) {
            System.out.println("No members found matching: " + query);
            return;
        }

        Member target = selectMember(scanner, results);
        if (target == null) return;

        System.out.println("\nCurrent details:");
        System.out.println(target.toDetailString());

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
                session.markChanged();
                System.out.println("\nRe-validating after update...");
                validationHandler.runInline();
            } else {
                System.out.println("Unknown field: " + fieldName);
            }
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
    }

    private Member selectMember(Scanner scanner, List<Member> results) {
        if (results.size() == 1) {
            System.out.println("\nFound: " + results.get(0).toSummary());
            return results.get(0);
        }
        System.out.printf("\nFound %d members:%n", results.size());
        for (int i = 0; i < results.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, results.get(i).toSummary());
        }
        System.out.print("Select member number: ");
        try {
            int idx = Integer.parseInt(scanner.nextLine().trim()) - 1;
            if (idx >= 0 && idx < results.size()) return results.get(idx);
            System.out.println("Invalid selection.");
        } catch (NumberFormatException e) {
            System.out.println("Invalid input.");
        }
        return null;
    }
}
