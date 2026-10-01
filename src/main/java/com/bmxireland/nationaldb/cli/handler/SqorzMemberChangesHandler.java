package com.bmxireland.nationaldb.cli.handler;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.bmxireland.nationaldb.cli.InputUtils;
import com.bmxireland.nationaldb.cli.MenuHandler;
import com.bmxireland.nationaldb.cli.SessionState;
import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.service.MemberService;
import com.bmxireland.nationaldb.service.MemberService.SqorzNewRider;
import com.bmxireland.nationaldb.service.MemberService.SqorzNewRidersResult;

/**
 * Reads a Sqorz Member Changes CSV and applies plate/transponder/club updates to matched
 * members ("changed" rows, matched by license number), and adds brand-new riders to the
 * database ("new" rows).
 */
@Component
public class SqorzMemberChangesHandler implements MenuHandler {

    private static final Logger log = LoggerFactory.getLogger(SqorzMemberChangesHandler.class);

    private final SessionState session;
    private final ValidationHandler validationHandler;
    private final MemberService memberService;

    public SqorzMemberChangesHandler(SessionState session, ValidationHandler validationHandler,
                                     MemberService memberService) {
        this.session = session;
        this.validationHandler = validationHandler;
        this.memberService = memberService;
    }

    @Override
    public void handle(Scanner scanner) {
        System.out.println("\n═══════════════ Incorporate Sqorz Member Changes ═══════════════");
        System.out.print("  Enter path to Sqorz Member Changes CSV file: ");
        String filePath = InputUtils.normalizeFilePath(scanner.nextLine());
        if (filePath == null || filePath.isEmpty()) {
            System.out.println("Cancelled.");
            return;
        }

        List<String[]> rows;
        try {
            rows = parseCsv(filePath);
        } catch (IOException e) {
            System.err.println("  ERROR: Could not read file: " + e.getMessage());
            log.error("Failed to load Sqorz member changes file: {}", filePath, e);
            return;
        }

        if (rows.isEmpty()) {
            System.out.println("  No data rows found in file.");
            return;
        }

        String[] header = rows.get(0);
        int idxType         = findColumn(header, "type");
        int idxMemberId     = findColumn(header, "memberId");
        int idxClub         = findColumn(header, "club");
        int idxPlate20      = findColumn(header, "plate20");
        int idxTransponder20 = findColumn(header, "transponder20");
        int idxFirstName    = findColumn(header, "firstName");
        int idxLastName     = findColumn(header, "lastName");
        int idxGender       = findColumn(header, "gender");
        int idxBirthDate    = findColumn(header, "birthDate");

        if (idxMemberId < 0) {
            System.err.println("  ERROR: CSV is missing required 'memberId' column.");
            return;
        }

        record AppliedChange(Member member, String field, String oldValue, String newValue) {}
        record SkippedRow(String memberId, String reason) {}

        List<AppliedChange> applied = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        List<SqorzNewRider> newRiders = new ArrayList<>();
        int unchanged = 0;

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);

            String type = get(row, idxType);

            if ("new".equalsIgnoreCase(type)) {
                newRiders.add(new SqorzNewRider(
                        get(row, idxMemberId), get(row, idxFirstName), get(row, idxLastName),
                        get(row, idxGender), get(row, idxBirthDate), get(row, idxClub), get(row, idxPlate20)));
                continue;
            }

            if (!"changed".equalsIgnoreCase(type)) continue;

            String memberId = get(row, idxMemberId);
            if (memberId.isEmpty()) continue;

            Member member = findByLicense(memberId);
            if (member == null) {
                skipped.add(new SkippedRow(memberId, "No member found with license number '" + memberId + "'"));
                log.warn("Sqorz member changes: no member found for license '{}'", memberId);
                continue;
            }

            String club         = get(row, idxClub);
            String plate20      = get(row, idxPlate20);
            String transponder20 = get(row, idxTransponder20);

            if (!club.isEmpty()) {
                String old = StringUtils.defaultString(member.getClubName());
                if (!club.equals(old)) {
                    member.setClubName(club);
                    applied.add(new AppliedChange(member, "Club", old, club));
                    log.info("Sqorz changes: set Club for {} [{}]: '{}' -> '{}'",
                            member.getGivenName() + " " + member.getFamilyName(), memberId, old, club);
                } else {
                    unchanged++;
                }
            }

            if (!plate20.isEmpty()) {
                String old = StringUtils.defaultString(member.getPlate20());
                if (!plate20.equals(old)) {
                    member.setPlate20(plate20);
                    applied.add(new AppliedChange(member, "Plate 20", old, plate20));
                    log.info("Sqorz changes: set Plate 20 for {} [{}]: '{}' -> '{}'",
                            member.getGivenName() + " " + member.getFamilyName(), memberId, old, plate20);
                } else {
                    unchanged++;
                }
            }

            if (!transponder20.isEmpty()) {
                String old = StringUtils.defaultString(member.getTransponder20());
                if (!transponder20.equals(old)) {
                    member.setTransponder20(transponder20);
                    applied.add(new AppliedChange(member, "Transponder 20", old, transponder20));
                    log.info("Sqorz changes: set Transponder 20 for {} [{}]: '{}' -> '{}'",
                            member.getGivenName() + " " + member.getFamilyName(), memberId, old, transponder20);
                } else {
                    unchanged++;
                }
            }
        }

        SqorzNewRidersResult newRidersResult = memberService.addNewRidersFromSqorzChanges(session.getMembers(), newRiders);

        System.out.println("─────────────────────────────────────────────────────────────────");
        for (AppliedChange c : applied) {
            System.out.printf("  Updated  %-25s  %-16s '%s' -> '%s'%n",
                    c.member().getGivenName() + " " + c.member().getFamilyName(),
                    c.field() + ":", c.oldValue(), c.newValue());
        }
        System.out.println("\n  ── Added (new riders) ──");
        if (newRidersResult.added().isEmpty()) {
            System.out.println("    None.");
        } else {
            for (Member m : newRidersResult.added()) {
                System.out.printf("    %-25s  licence: %-12s  club: %s%n",
                        m.getGivenName() + " " + m.getFamilyName(), m.getLicenseNumber(), m.getClubName());
                log.info("Sqorz changes: added new rider {} [{}]", m.getGivenName() + " " + m.getFamilyName(), m.getLicenseNumber());
            }
        }
        for (SkippedRow s : skipped) {
            System.out.printf("  SKIPPED  %s — %s%n", s.memberId(), s.reason());
        }
        for (SqorzNewRidersResult.SkippedRider s : newRidersResult.skipped()) {
            System.out.printf("  SKIPPED  %s — %s%n", s.licenseNumber(), s.reason());
            log.warn("Sqorz member changes: skipped new rider '{}' — {}", s.licenseNumber(), s.reason());
        }
        System.out.printf("  Done. %d change(s) applied, %d unchanged, %d new rider(s) added, %d skipped.%n",
                applied.size(), unchanged, newRidersResult.added().size(),
                skipped.size() + newRidersResult.skipped().size());
        System.out.println("═════════════════════════════════════════════════════════════════");

        if (!applied.isEmpty() || !newRidersResult.added().isEmpty()) {
            session.markChanged();
            System.out.println("\nRe-validating...");
            validationHandler.runInline();
        }
    }

    private Member findByLicense(String licenseNumber) {
        String target = licenseNumber.toLowerCase();
        return session.getMembers().stream()
                .filter(m -> target.equals(StringUtils.defaultString(m.getLicenseNumber()).toLowerCase()))
                .findFirst()
                .orElse(null);
    }

    private String get(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return "";
        return StringUtils.defaultString(row[idx]).trim();
    }

    private int findColumn(String[] header, String name) {
        for (int i = 0; i < header.length; i++) {
            if (name.equalsIgnoreCase(header[i].trim())) return i;
        }
        return -1;
    }

    private List<String[]> parseCsv(String filePath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(splitCsvLine(line));
                }
            }
        }
        return rows;
    }

    private String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        int i = 0;
        while (i <= line.length()) {
            if (i == line.length()) {
                fields.add("");
                break;
            }
            if (line.charAt(i) == '"') {
                // Quoted field
                int end = line.indexOf('"', i + 1);
                if (end < 0) end = line.length();
                fields.add(line.substring(i + 1, end));
                i = end + 1;
                if (i < line.length() && line.charAt(i) == ',') i++;
            } else {
                int end = line.indexOf(',', i);
                if (end < 0) {
                    fields.add(line.substring(i));
                    break;
                }
                fields.add(line.substring(i, end));
                i = end + 1;
            }
        }
        return fields.toArray(new String[0]);
    }
}
