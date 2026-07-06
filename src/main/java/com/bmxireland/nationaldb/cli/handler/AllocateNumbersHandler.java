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
import com.bmxireland.nationaldb.service.MemberService.AllocationRequest;
import com.bmxireland.nationaldb.service.MemberService.AllocationResult;

/**
 * Allocates Plate 20 numbers to riders named in pasted "BMX Application Form" emails.
 * Numbers are only issued to riders matched by an exact licence number already in
 * the database; unmatched or already-assigned requests are reported and skipped.
 */
@Component
public class AllocateNumbersHandler implements MenuHandler {

    private static final Logger log = LoggerFactory.getLogger(AllocateNumbersHandler.class);

    private final MemberService memberService;
    private final ValidationHandler validationHandler;
    private final SessionState session;

    public AllocateNumbersHandler(MemberService memberService, ValidationHandler validationHandler,
                                   SessionState session) {
        this.memberService = memberService;
        this.validationHandler = validationHandler;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.println("\n═══════════════ Allocate Race Numbers (Plate 20) ═══════════════");
        System.out.println("Paste one or more BMX Application Form emails below.");
        System.out.println("Enter a line containing only END when finished.");
        System.out.println("──────────────────────────────────────────────────────────────────");

        StringBuilder pasted = new StringBuilder();
        while (true) {
            String line = scanner.nextLine();
            if (line.trim().equalsIgnoreCase("END")) break;
            pasted.append(line).append('\n');
        }

        List<AllocationRequest> requests = MemberService.parseAllocationRequests(pasted.toString());
        if (requests.isEmpty()) {
            System.out.println("No requests found in pasted text.");
            return;
        }

        AllocationResult result = memberService.allocateRaceNumbers(
                session.getMembers(), requests, MemberService.BULK_UPDATE_FIELD);

        System.out.printf("%n  Parsed %d request(s).%n", requests.size());

        System.out.println("\n  ── To allocate ──");
        if (result.allocated().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (AllocationResult.Allocated a : result.allocated()) {
                Member m = a.member();
                System.out.printf("    %4d  —  %s %s  [%s]%n",
                        a.plateNumber(),
                        m.getGivenName()  != null ? m.getGivenName()  : "",
                        m.getFamilyName() != null ? m.getFamilyName() : "",
                        m.getLicenseNumber());
            }
        }

        System.out.println("\n  ── Skipped ──");
        if (result.skipped().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (AllocationResult.SkippedRequest s : result.skipped()) {
                System.out.printf("    %-25s [%s]  — %s%n",
                        s.request().riderName() != null ? s.request().riderName() : "(unknown)",
                        s.request().licenseNumber() != null ? s.request().licenseNumber() : "no licence",
                        s.reason());
            }
        }
        System.out.println("═══════════════════════════════════════════════════════════════");

        if (result.allocated().isEmpty()) {
            return;
        }

        System.out.printf("%nConfirm allocation of %d number(s)? (Y/N): ", result.allocated().size());
        if (!"Y".equalsIgnoreCase(scanner.nextLine().trim())) {
            System.out.println("Cancelled. No numbers were allocated.");
            return;
        }

        memberService.applyAllocations(result.allocated(), MemberService.BULK_UPDATE_FIELD);
        session.markChanged();
        for (AllocationResult.Allocated a : result.allocated()) {
            log.info("Allocated Plate 20 '{}' to {} {} [{}]", a.plateNumber(),
                    a.member().getGivenName(), a.member().getFamilyName(), a.member().getLicenseNumber());
        }
        System.out.printf("Allocated %d number(s).%n", result.allocated().size());

        System.out.println("\nRe-validating...");
        validationHandler.runInline();
    }
}
