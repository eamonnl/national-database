package com.bmxireland.nationaldb.cli.handler;

import java.util.List;
import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.MemberService;

/**
 * Handles searching for and displaying member records.
 */
@Component
public class SearchHandler implements MenuHandler {

    private final MemberService memberService;
    private final SessionState session;

    public SearchHandler(MemberService memberService, SessionState session) {
        this.memberService = memberService;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.print("\nSearch by name or license number: ");
        String query = scanner.nextLine().trim();
        if (query.isEmpty()) return;

        List<Member> results = memberService.search(session.getMembers(), query);
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
}
