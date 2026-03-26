package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service encapsulating business operations on the member list.
 * All mutating operations go through this service so they can be invoked
 * from any presentation layer (CLI, web, etc.) without duplication.
 */
@Service
public class MemberService {

    static final String BULK_UPDATE_FIELD = "Plate 20";
    static final int STALE_LICENSE_YEARS = 3;

    private final DatabaseService databaseService;

    public MemberService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    // ---- Result types ----

    public record BulkUpdateResult(List<AppliedUpdate> applied, List<SkippedEntry> skipped) {
        public record AppliedUpdate(Member member, String oldValue, String newValue) {}
        public record SkippedEntry(String input, String reason, List<Member> matches) {}
    }

    public record AvailableNumbersResult(
            String fieldName,
            LocalDate staleCutoff,
            List<StaleAssignment> reclaimable,
            List<Integer> unassigned,
            int maxRange) {
        public record StaleAssignment(int plateNumber, Member member) {}

        public int totalAvailable() {
            return reclaimable.size() + unassigned.size();
        }
    }

    // ---- Public operations ----

    /**
     * Searches members by name (given, family, or full name) or license number.
     * Matching is case-insensitive substring.
     */
    public List<Member> search(List<Member> members, String query) {
        String lower = query.toLowerCase();
        return members.stream()
                .filter(m -> {
                    String lic    = m.getLicenseNumber() != null ? m.getLicenseNumber().toLowerCase() : "";
                    String given  = m.getGivenName()     != null ? m.getGivenName().toLowerCase()     : "";
                    String family = m.getFamilyName()    != null ? m.getFamilyName().toLowerCase()    : "";
                    String club   = m.getClubName()      != null ? m.getClubName().toLowerCase()      : "";
                    return lic.contains(lower)
                            || given.contains(lower)
                            || family.contains(lower)
                            || club.contains(lower)
                            || (given + " " + family).contains(lower);
                })
                .collect(Collectors.toList());
    }

    /**
     * Processes "Name, PlateNumber" input lines and updates the Plate 20 field for
     * members that are uniquely identified by the given name.
     * Entries that cannot be uniquely matched are recorded as skipped with a reason.
     */
    public BulkUpdateResult bulkUpdatePlate20(List<Member> members, List<String> inputLines) {
        List<BulkUpdateResult.AppliedUpdate> applied = new ArrayList<>();
        List<BulkUpdateResult.SkippedEntry> skipped  = new ArrayList<>();

        for (String line : inputLines) {
            if (line == null || line.isBlank()) continue;

            int commaIdx = line.lastIndexOf(',');
            if (commaIdx == -1) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "Invalid format: expected 'Name, PlateNumber'", List.of()));
                continue;
            }

            String name        = line.substring(0, commaIdx).trim();
            String plateNumber = line.substring(commaIdx + 1).trim();

            if (name.isEmpty() || plateNumber.isEmpty()) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "Name or plate number is empty", List.of()));
                continue;
            }

            List<Member> matches = search(members, name);
            if (matches.isEmpty()) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "No member found for name '" + name + "'", List.of()));
            } else if (matches.size() > 1) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "Name '" + name + "' matched " + matches.size() + " members (not unique)", matches));
            } else {
                Member target  = matches.get(0);
                String oldValue = databaseService.getFieldValue(target, BULK_UPDATE_FIELD);
                databaseService.updateMemberField(target, BULK_UPDATE_FIELD, plateNumber);
                applied.add(new BulkUpdateResult.AppliedUpdate(target, oldValue, plateNumber));
            }
        }

        return new BulkUpdateResult(applied, skipped);
    }

    /**
     * Returns available plate numbers above 100 for the given plate category field name.
     * A number is available if it is unassigned in the range 101..maxRange, or is assigned
     * to a member whose license expired 3 or more years ago.
     */
    public AvailableNumbersResult getAvailableRaceNumbers(List<Member> members, String fieldName) {
        LocalDate cutoff = LocalDate.now().minusYears(STALE_LICENSE_YEARS);

        TreeMap<Integer, Member> assignedAbove100 = new TreeMap<>();
        for (Member m : members) {
            String plateStr = databaseService.getFieldValue(m, fieldName);
            if (plateStr == null || plateStr.isBlank()) continue;
            try {
                int num = Integer.parseInt(plateStr.trim());
                if (num > 100) {
                    assignedAbove100.put(num, m);
                }
            } catch (NumberFormatException ignored) {
                // non-numeric plate value — skip
            }
        }

        List<AvailableNumbersResult.StaleAssignment> reclaimable = new ArrayList<>();
        for (Map.Entry<Integer, Member> entry : assignedAbove100.entrySet()) {
            if (isLicenseStale(entry.getValue(), cutoff)) {
                reclaimable.add(new AvailableNumbersResult.StaleAssignment(entry.getKey(), entry.getValue()));
            }
        }

        int maxAssigned = assignedAbove100.isEmpty() ? 100 : assignedAbove100.lastKey();
        int maxRange    = Math.max(maxAssigned + 20, 120);
        List<Integer> unassigned = new ArrayList<>();
        for (int n = 101; n <= maxRange; n++) {
            if (!assignedAbove100.containsKey(n)) {
                unassigned.add(n);
            }
        }

        return new AvailableNumbersResult(fieldName, cutoff, reclaimable, unassigned, maxRange);
    }

    /**
     * Returns true if the member's license expired before the given cutoff date.
     * Returns false if the expiry date is missing or cannot be parsed.
     */
    public boolean isLicenseStale(Member member, LocalDate cutoff) {
        String expiry = member.getLicenseExpiry();
        if (expiry == null || expiry.isBlank()) return false;
        try {
            return LocalDate.parse(expiry.trim()).isBefore(cutoff);
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
