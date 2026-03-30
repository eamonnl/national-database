package com.bmxireland.nationaldb.cli.handler;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.MemberService;
import com.bmxireland.nationaldb.service.MemberService.BulkUpdateResult;

/**
 * Accepts a pasted list of name/number pairs and sets Plate 20 for each matched member.
 * Accepted formats:  "First Last #342"  or  "First Last = 342"
 */
@Component
public class BulkUpdateHandler implements MenuHandler {

    private static final Logger log = LoggerFactory.getLogger(BulkUpdateHandler.class);

    private final MemberService memberService;
    private final ValidationHandler validationHandler;
    private final SessionState session;

    public BulkUpdateHandler(MemberService memberService, ValidationHandler validationHandler,
                              SessionState session) {
        this.memberService = memberService;
        this.validationHandler = validationHandler;
        this.session = session;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.println("\n═══════════════ Bulk Update Race Numbers (Plate 20) ═══════════════");
        System.out.println("Enter entries as 'First Last  #PlateNumber' or 'First Last = PlateNumber', one per line.");
        System.out.println("Trailing notes are ignored, e.g. 'Adam Hosback  #342  (needs full license)'");
        System.out.println("Enter an empty line when done.");
        System.out.println("─────────────────────────────────────────────────────────────────");

        List<String> inputLines = new ArrayList<>();
        while (true) {
            System.out.print("> ");
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) break;
            inputLines.add(line);
        }

        BulkUpdateResult result = memberService.bulkUpdatePlate20(session.getMembers(), inputLines);

        System.out.println("─────────────────────────────────────────────────────────────────");
        for (BulkUpdateResult.AppliedUpdate u : result.applied()) {
            System.out.printf("  Updated %-30s Plate 20: '%s' -> '%s'%n",
                    u.member().getGivenName() + " " + u.member().getFamilyName(),
                    u.oldValue() != null ? u.oldValue() : "(empty)", u.newValue());
            log.info("Bulk update: set Plate 20 for {} {} [{}] to '{}'",
                    u.member().getGivenName(), u.member().getFamilyName(),
                    u.member().getLicenseNumber(), u.newValue());
        }
        for (BulkUpdateResult.SkippedEntry s : result.skipped()) {
            System.out.printf("  ERROR: %s — input: '%s'%n", s.reason(), s.input());
            if (!s.matches().isEmpty()) {
                for (Member m : s.matches()) System.out.printf("           %s%n", m.toSummary());
            }
            log.error("Bulk update skipped: {} — input: '{}'", s.reason(), s.input());
        }
        System.out.printf("  Done. %d updated, %d skipped.%n",
                result.applied().size(), result.skipped().size());

        if (!result.applied().isEmpty()) {
            session.markChanged();
            System.out.println("\nRe-validating...");
            validationHandler.runInline();
        }
    }
}
