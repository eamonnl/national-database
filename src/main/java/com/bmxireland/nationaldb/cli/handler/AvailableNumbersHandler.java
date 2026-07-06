package com.bmxireland.nationaldb.cli.handler;

import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.MemberService;
import com.bmxireland.nationaldb.service.MemberService.AvailableNumbersResult;

/**
 * Lists unassigned Plate 20 numbers and numbers reclaimable from stale licences,
 * and offers to reclaim the latter.
 */
@Component
public class AvailableNumbersHandler implements MenuHandler {

    private static final Logger log = LoggerFactory.getLogger(AvailableNumbersHandler.class);

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
        printReport(result);

        if (result.reclaimable().isEmpty()) {
            return;
        }

        System.out.print("\n[R]eclaim these numbers / [Enter] to skip: ");
        String choice = scanner.nextLine().trim().toUpperCase();
        if (!choice.equals("R")) {
            return;
        }

        for (AvailableNumbersResult.StaleAssignment s : result.reclaimable()) {
            Member m = s.member();
            log.info("Reclaiming {} '{}' from {} {} [{}] (stale license)",
                    result.fieldName(), s.plateNumber(),
                    m.getGivenName(), m.getFamilyName(), m.getLicenseNumber());
        }
        memberService.reclaimNumbers(result.reclaimable(), result.fieldName());
        session.markChanged();
        System.out.printf("%nReclaimed %d number(s).%n", result.reclaimable().size());

        System.out.println("\n═══════════════ Available Race Numbers (Plate 20) ═══════════════");
        printReport(memberService.getAvailableRaceNumbers(session.getMembers(), result.fieldName()));
    }

    private void printReport(AvailableNumbersResult result) {
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
        printUnassignedRanges(result.unassignedRanges());

        System.out.printf("%n  Total available: %d (%d reclaimable, %d unassigned in range 101–%d)%n",
                result.totalAvailable(), result.reclaimable().size(),
                result.unassigned().size(), result.maxRange());
        System.out.println("═════════════════════════════════════════════════════════════");
    }

    private void printUnassignedRanges(List<AvailableNumbersResult.NumberRange> ranges) {
        if (ranges.isEmpty()) {
            System.out.println("    None.");
            return;
        }
        StringBuilder row = new StringBuilder("    ");
        for (int i = 0; i < ranges.size(); i++) {
            AvailableNumbersResult.NumberRange r = ranges.get(i);
            String label = r.size() == 1
                    ? String.valueOf(r.start())
                    : r.start() + "–" + r.end() + " (" + r.size() + ")";
            row.append(String.format("%-16s", label));
            if ((i + 1) % 5 == 0) {
                System.out.println(row);
                row = new StringBuilder("    ");
            }
        }
        if (row.length() > 4) System.out.println(row);
    }
}
