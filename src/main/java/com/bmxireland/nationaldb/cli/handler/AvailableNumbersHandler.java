package com.bmxireland.nationaldb.cli.handler;

import java.util.List;
import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.MemberService;
import com.bmxireland.nationaldb.service.MemberService.AvailableNumbersResult;

/**
 * Lists unassigned Plate 20 numbers and numbers reclaimable from stale licences.
 */
@Component
public class AvailableNumbersHandler implements MenuHandler {

    private final MemberService memberService;
    private final SessionState session;

    public AvailableNumbersHandler(MemberService memberService, SessionState session) {
        this.memberService = memberService;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.println("\n═══════════════ Available Race Numbers (Plate 20) ═══════════════");

        AvailableNumbersResult result = memberService.getAvailableRaceNumbers(
                session.getMembers(), MemberService.BULK_UPDATE_FIELD);

        System.out.printf("%n  Category     : %s%n", result.fieldName());
        System.out.printf("  Stale cutoff : licenses expired before %s (3+ years ago)%n", result.staleCutoff());

        System.out.println("\n  ── Reclaimable (assigned to riders with stale licenses) ──");
        if (result.reclaimable().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (AvailableNumbersResult.StaleAssignment s : result.reclaimable()) {
                Member m = s.member();
                System.out.printf("    %4d  —  %s %s  [%s]  expired: %s%n",
                        s.plateNumber(),
                        m.getGivenName()  != null ? m.getGivenName()  : "",
                        m.getFamilyName() != null ? m.getFamilyName() : "",
                        m.getLicenseNumber(),
                        m.getLicenseExpiry() != null ? m.getLicenseExpiry() : "unknown");
            }
        }

        System.out.printf("%n  ── Unassigned numbers in range 101–%d ──%n", result.maxRange());
        if (result.unassigned().isEmpty()) {
            System.out.println("    None.");
        } else {
            StringBuilder row = new StringBuilder("    ");
            List<Integer> unassigned = result.unassigned();
            for (int i = 0; i < unassigned.size(); i++) {
                row.append(String.format("%4d", unassigned.get(i)));
                if ((i + 1) % 15 == 0) {
                    System.out.println(row);
                    row = new StringBuilder("    ");
                }
            }
            if (row.length() > 4) System.out.println(row);
        }

        System.out.printf("%n  Total available: %d (%d reclaimable, %d unassigned in range 101–%d)%n",
                result.totalAvailable(), result.reclaimable().size(),
                result.unassigned().size(), result.maxRange());
        System.out.println("═════════════════════════════════════════════════════════════");
    }
}
