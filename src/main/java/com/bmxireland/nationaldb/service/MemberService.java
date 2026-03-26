package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.model.RegistrationEntry;
import org.apache.commons.lang3.StringUtils;
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
                    String lic    = StringUtils.defaultString(m.getLicenseNumber()).toLowerCase();
                    String given  = StringUtils.defaultString(m.getGivenName()).toLowerCase();
                    String family = StringUtils.defaultString(m.getFamilyName()).toLowerCase();
                    String club   = StringUtils.defaultString(m.getClubName()).toLowerCase();
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

            int hashIdx = line.indexOf('#');
            if (hashIdx == -1) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "Invalid format: expected 'Name #123'", List.of()));
                continue;
            }

            String name = line.substring(0, hashIdx).trim();

            // Extract leading digits after '#'; anything following (spaces, notes) is ignored
            String afterHash = line.substring(hashIdx + 1).trim();
            int digitEnd = 0;
            while (digitEnd < afterHash.length() && Character.isDigit(afterHash.charAt(digitEnd))) {
                digitEnd++;
            }
            String plateNumber = afterHash.substring(0, digitEnd);

            if (name.isEmpty()) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "Name is empty", List.of()));
                continue;
            }
            if (plateNumber.isEmpty()) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "No plate number found after '#'", List.of()));
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
     * Imports registration data from a Cycling Ireland export file into the member list.
     *
     * Matching is attempted in priority order:
     *   1. By MID (internationalLicense) — most reliable, survives annual license changes
     *   2. By current licenseNumber — exact match
     *   3. By full name + DOB — fallback for renewals where MID and license differ
     *
     * On match: licenseNumber, licenseExpiry, and active are updated. internationalLicense
     * is set if it was previously empty (fixing the root cause of duplicate rows).
     *
     * On no match for a NEW entry: a new member row is added.
     * On no match for a RENEWAL entry: the row is skipped and logged.
     */
    public ImportResult importRegistrationData(List<Member> members, List<RegistrationEntry> entries) {
        List<ImportResult.UpdatedEntry> updated = new ArrayList<>();
        List<Member>                   added   = new ArrayList<>();
        List<ImportResult.SkippedEntry> skipped = new ArrayList<>();

        // Build lookup maps for fast matching
        Map<String, Member> byMid     = new HashMap<>();
        Map<String, Member> byLicense = new HashMap<>();
        for (Member m : members) {
            if (m.getInternationalLicense() != null && !m.getInternationalLicense().isBlank()) {
                byMid.put(m.getInternationalLicense().trim(), m);
            }
            if (m.getLicenseNumber() != null && !m.getLicenseNumber().isBlank()) {
                byLicense.put(m.getLicenseNumber().trim(), m);
            }
        }

        for (RegistrationEntry entry : entries) {
            Member match = null;
            String matchMethod = null;

            // 1. Match by MID
            if (entry.memberId() != null && !entry.memberId().isBlank()) {
                match = findByMid(byMid, entry.memberId().trim());
                if (match != null) matchMethod = "MID";
            }

            // 2. Match by licence number
            if (match == null && entry.licenseNumber() != null && !entry.licenseNumber().isBlank()) {
                match = byLicense.get(entry.licenseNumber().trim());
                if (match != null) matchMethod = "licence number";
            }

            // 3. Fallback: name + DOB (for renewals where licence changed and MID was missing)
            if (match == null) {
                String fullName = trim(entry.firstName()) + " " + trim(entry.lastName());
                List<Member> nameMatches = search(members, fullName.trim());
                List<Member> dobMatches = nameMatches.stream()
                        .filter(m -> trim(entry.dateOfBirth()).equalsIgnoreCase(trim(m.getBirthDate())))
                        .collect(Collectors.toList());
                if (dobMatches.size() == 1) {
                    match = dobMatches.get(0);
                    matchMethod = "name + DOB";
                } else if (dobMatches.size() > 1) {
                    skipped.add(new ImportResult.SkippedEntry(entry.firstName(), entry.lastName(),
                            entry.licenseNumber(),
                            "Ambiguous: name + DOB matched " + dobMatches.size() + " members"));
                    continue;
                }
            }

            if (match != null) {
                String oldLicense = match.getLicenseNumber();
                match.setLicenseNumber(entry.licenseNumber());
                match.setLicenseExpiry(entry.expiryDate());
                match.setActive("Yes");
                // Backfill MID if missing — fixes root cause of duplicate row problem
                if ((match.getInternationalLicense() == null || match.getInternationalLicense().isBlank())
                        && entry.memberId() != null && !entry.memberId().isBlank()) {
                    match.setInternationalLicense(entry.memberId());
                }
                updated.add(new ImportResult.UpdatedEntry(match, oldLicense, entry.licenseNumber(), matchMethod));

                // Keep lookup maps current so later entries in the same file don't re-match
                if (entry.licenseNumber() != null) byLicense.put(entry.licenseNumber().trim(), match);

            } else if ("NEW".equalsIgnoreCase(trim(entry.newOrRenewal()))) {
                Member newMember = buildMember(entry, members.size() + added.size());
                added.add(newMember);
                if (newMember.getLicenseNumber() != null) byLicense.put(newMember.getLicenseNumber().trim(), newMember);
                if (newMember.getInternationalLicense() != null) byMid.put(newMember.getInternationalLicense().trim(), newMember);

            } else {
                skipped.add(new ImportResult.SkippedEntry(entry.firstName(), entry.lastName(),
                        entry.licenseNumber(), "RENEWAL not matched to any existing member"));
            }
        }

        members.addAll(added);
        return new ImportResult(updated, added, skipped);
    }

    public record ImportResult(
            List<UpdatedEntry> updated,
            List<Member> added,
            List<SkippedEntry> skipped) {

        public record UpdatedEntry(Member member, String oldLicenseNumber,
                                   String newLicenseNumber, String matchMethod) {}

        public record SkippedEntry(String firstName, String lastName,
                                   String licenseNumber, String reason) {}
    }

    // ---- Private helpers ----

    private Member findByMid(Map<String, Member> byMid, String mid) {
        Member exact = byMid.get(mid);
        if (exact != null) return exact;
        // Numeric comparison to handle leading-zero differences (e.g. "0289679" vs "289679")
        try {
            long numericMid = Long.parseLong(mid);
            for (Map.Entry<String, Member> e : byMid.entrySet()) {
                try {
                    if (Long.parseLong(e.getKey()) == numericMid) return e.getValue();
                } catch (NumberFormatException ignored) {}
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private Member buildMember(RegistrationEntry entry, int rowIndex) {
        Member m = new Member();
        m.setRowIndex(rowIndex);
        m.setLicenseNumber(entry.licenseNumber());
        m.setLicenseClass(entry.category());
        m.setLicenseExpiry(entry.expiryDate());
        m.setGivenName(entry.firstName());
        m.setFamilyName(entry.lastName());
        m.setBirthDate(entry.dateOfBirth());
        m.setGender(normalizeGender(entry.gender()));
        m.setActive("Yes");
        m.setInternationalLicense(entry.memberId());
        m.setClubName(entry.club());
        m.setEmail(entry.email());
        m.setLicenseCountryCode(entry.nationality());
        m.setEmergencyContactPerson(entry.emergencyContactName());
        m.setEmergencyContactNumber(entry.emergencyContactPhone());
        return m;
    }

    private String normalizeGender(String gender) {
        if (gender == null) return null;
        return switch (gender.trim().toUpperCase()) {
            case "MALE"   -> "Male";
            case "FEMALE" -> "Female";
            default       -> gender.trim();
        };
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
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
