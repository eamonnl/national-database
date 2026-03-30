package com.bmxireland.nationaldb.cli.handler;

import java.util.List;
import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;

/**
 * Pages through all members in the database, 20 per screen.
 */
@Component
public class ListMembersHandler implements MenuHandler {

    private static final int PAGE_SIZE = 20;

    private final SessionState session;

    public ListMembersHandler(SessionState session) {
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        List<Member> members = session.getMembers();
        int totalPages = (members.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int currentPage = 0;

        while (true) {
            int start = currentPage * PAGE_SIZE;
            int end   = Math.min(start + PAGE_SIZE, members.size());

            System.out.printf("\n═══ Members %d-%d of %d (Page %d/%d) ═══%n",
                    start + 1, end, members.size(), currentPage + 1, totalPages);

            for (int i = start; i < end; i++) {
                System.out.printf("  %4d. %s%n", i + 1, members.get(i).toSummary());
            }

            System.out.print("\n[N]ext / [P]rev / [Q]uit listing: ");
            switch (scanner.nextLine().trim().toUpperCase()) {
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
}
