package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.model.RegistrationEntry;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Service encapsulating business operations on the member list.
 * All mutating operations go through this service so they can be invoked
 * from any presentation layer (CLI, web, etc.) without duplication.
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    public static final String BULK_UPDATE_FIELD = "Plate 20";
    static final int STALE_LICENSE_YEARS = 3;

    private final DatabaseService databaseService;
    private final MemberMatcherFactory matcherFactory;

    public MemberService(DatabaseService databaseService, MemberMatcherFactory matcherFactory) {
        this.databaseService = databaseService;
        this.matcherFactory  = matcherFactory;
    }

    // ---- Result types ----

    public record BulkUpdateResult(List<AppliedUpdate> applied, List<SkippedEntry> skipped, List<UnchangedEntry> unchanged) {
        public record AppliedUpdate(Member member, String oldValue, String newValue) {}
        public record SkippedEntry(String input, String reason, List<Member> matches) {}
        public record UnchangedEntry(Member member, String value) {}
    }

    public record AvailableNumbersResult(
            String fieldName,
            LocalDate staleCutoff,
            List<StaleAssignment> reclaimable,
            List<Integer> unassigned,
            int maxRange) {
        public record StaleAssignment(int plateNumber, Member member) {}

        /** An inclusive contiguous run of unassigned numbers, e.g. 101-105. */
        public record NumberRange(int start, int end) {
            public int size() { return end - start + 1; }
        }

        public int totalAvailable() {
            return reclaimable.size() + unassigned.size();
        }

        /**
         * Groups {@link #unassigned()} into contiguous inclusive ranges, since race
         * numbers are usually allocated to riders in blocks. Relies on unassigned
         * being sorted ascending with no duplicates, which getAvailableRaceNumbers
         * guarantees.
         */
        public List<NumberRange> unassignedRanges() {
            List<NumberRange> ranges = new ArrayList<>();
            int i = 0;
            while (i < unassigned.size()) {
                int start = unassigned.get(i);
                int end = start;
                while (i + 1 < unassigned.size() && unassigned.get(i + 1) == end + 1) {
                    i++;
                    end = unassigned.get(i);
                }
                ranges.add(new NumberRange(start, end));
                i++;
            }
            return ranges;
        }
    }

    /** A single "BMX Application Form" email, parsed into its fields. */
    public record AllocationRequest(
            String riderName,
            String riderEmail,
            String riderAge,
            String riderGender,
            String riderDob,
            String riderClub,
            String licenseNumber) {}

    public record AllocationResult(List<Allocated> allocated, List<SkippedRequest> skipped) {
        public record Allocated(AllocationRequest request, Member member, int plateNumber) {}
        public record SkippedRequest(AllocationRequest request, String reason) {}
    }

    // ---- Public operations ----

    /**
     * Searches members by name (given, family, or full name) or license number.
     * Matching is case-insensitive substring.
     */
    public List<Member> search(List<Member> members, String query) {
        String lower           = query.toLowerCase();
        // Normalised name matching only applies to pure-name queries (no digits).
        // Queries like "LIC002" are identifier lookups and must not be normalised,
        // as stripping non-alpha chars reduces "LIC002" to "lic" which is a substring of "alice".
        boolean isNameQuery    = !query.chars().anyMatch(Character::isDigit);
        String normalizedQuery = isNameQuery ? normalizeNameForMatch(query) : "";
        return members.stream()
                .filter(m -> {
                    String lic    = StringUtils.defaultString(m.getLicenseNumber()).toLowerCase();
                    String given  = StringUtils.defaultString(m.getGivenName()).toLowerCase();
                    String family = StringUtils.defaultString(m.getFamilyName()).toLowerCase();
                    String club   = StringUtils.defaultString(m.getClubName()).toLowerCase();
                    if (lic.contains(lower) || given.contains(lower) || family.contains(lower)
                            || club.contains(lower) || (given + " " + family).contains(lower)) {
                        return true;
                    }
                    if (!isNameQuery || normalizedQuery.isEmpty()) return false;
                    String normalizedFull = normalizeNameForMatch(
                            StringUtils.defaultString(m.getGivenName()) + " " +
                            StringUtils.defaultString(m.getFamilyName()));
                    return normalizedFull.contains(normalizedQuery);
                })
                .collect(Collectors.toList());
    }

    /**
     * Processes bulk plate-number input lines and updates the Plate 20 field for
     * members that are uniquely identified by the given name.
     * Accepted formats:
     *   "Name #123"   — hash separator
     *   "Name = 123"  — equals separator (copy-paste from spreadsheet/text)
     * Entries that cannot be uniquely matched are recorded as skipped with a reason.
     */
    public BulkUpdateResult bulkUpdatePlate20(List<Member> members, List<String> inputLines) {
        List<BulkUpdateResult.AppliedUpdate> applied    = new ArrayList<>();
        List<BulkUpdateResult.SkippedEntry> skipped     = new ArrayList<>();
        List<BulkUpdateResult.UnchangedEntry> unchanged = new ArrayList<>();

        for (String line : inputLines) {
            if (line == null || line.isBlank()) continue;

            // Detect separator: '=' takes priority; fall back to '#'
            int sepIdx = line.indexOf('=');
            if (sepIdx == -1) {
                sepIdx = line.indexOf('#');
            }
            if (sepIdx == -1) {
                skipped.add(new BulkUpdateResult.SkippedEntry(
                        line, "Invalid format: expected 'Name = 123' or 'Name #123'", List.of()));
                continue;
            }

            // Normalise internal whitespace in the name (e.g. double spaces)
            String name = StringUtils.normalizeSpace(line.substring(0, sepIdx).trim());

            // Extract leading digits after the separator; anything following is ignored
            String afterSep = line.substring(sepIdx + 1).trim();
            int digitEnd = 0;
            while (digitEnd < afterSep.length() && Character.isDigit(afterSep.charAt(digitEnd))) {
                digitEnd++;
            }
            String plateNumber = afterSep.substring(0, digitEnd);

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
                if (plateNumber.equals(oldValue)) {
                    unchanged.add(new BulkUpdateResult.UnchangedEntry(target, plateNumber));
                    continue;
                }
                databaseService.updateMemberField(target, BULK_UPDATE_FIELD, plateNumber);
                applied.add(new BulkUpdateResult.AppliedUpdate(target, oldValue, plateNumber));
            }
        }

        return new BulkUpdateResult(applied, skipped, unchanged);
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
        int maxRange    = Math.min(Math.max(maxAssigned + 20, 120), 999);
        List<Integer> unassigned = new ArrayList<>();
        for (int n = 101; n <= maxRange; n++) {
            if (!assignedAbove100.containsKey(n)) {
                unassigned.add(n);
            }
        }

        return new AvailableNumbersResult(fieldName, cutoff, reclaimable, unassigned, maxRange);
    }

    /**
     * Clears the given field for each stale assignment, freeing those numbers for
     * reassignment. Callers typically pass {@code AvailableNumbersResult.reclaimable()}.
     */
    public void reclaimNumbers(List<AvailableNumbersResult.StaleAssignment> assignments, String fieldName) {
        for (AvailableNumbersResult.StaleAssignment assignment : assignments) {
            databaseService.updateMemberField(assignment.member(), fieldName, null);
        }
    }

    private static final Map<String, String> ALLOCATION_FIELD_LABELS = Map.of(
            "rider name", "name",
            "rider email", "email",
            "rider age", "age",
            "rider gender", "gender",
            "rider dob", "dob",
            "rider club", "club",
            "cycling ireland license number", "license");

    /**
     * Parses one or more pasted "BMX Application Form" email bodies into requests.
     * A new request starts at each "Rider Name:" line; unrecognised lines (email
     * boilerplate, blank lines) are ignored.
     */
    public static List<AllocationRequest> parseAllocationRequests(String rawText) {
        List<AllocationRequest> requests = new ArrayList<>();
        Map<String, String> fields = new HashMap<>();

        for (String line : rawText.split("\\R")) {
            int sep = line.indexOf(':');
            if (sep == -1) continue;
            String key = ALLOCATION_FIELD_LABELS.get(line.substring(0, sep).trim().toLowerCase());
            if (key == null) continue;
            String value = line.substring(sep + 1).trim();

            if (key.equals("name") && !fields.isEmpty()) {
                requests.add(toAllocationRequest(fields));
                fields = new HashMap<>();
            }
            fields.put(key, value);
        }
        if (!fields.isEmpty()) {
            requests.add(toAllocationRequest(fields));
        }
        return requests;
    }

    private static AllocationRequest toAllocationRequest(Map<String, String> fields) {
        return new AllocationRequest(
                fields.get("name"), fields.get("email"), fields.get("age"),
                fields.get("gender"), fields.get("dob"), fields.get("club"),
                fields.get("license"));
    }

    /**
     * Matches each request to an existing member by exact (case-insensitive) licence
     * number and proposes the next available number, lowest first, for each match.
     * Does not mutate any member — pair with {@link #applyAllocations} to commit.
     * A request is skipped, rather than allocated, when: it has no licence number;
     * the licence number matches no member; the member already has a value in
     * fieldName; or a duplicate request in this same batch already claimed the
     * member's next number; or the available-number pool is exhausted.
     */
    public AllocationResult allocateRaceNumbers(List<Member> members, List<AllocationRequest> requests, String fieldName) {
        Map<String, Member> byLicense = new HashMap<>();
        for (Member m : members) {
            if (StringUtils.isNotBlank(m.getLicenseNumber())) {
                byLicense.put(m.getLicenseNumber().trim().toUpperCase(), m);
            }
        }

        Deque<Integer> pool = new ArrayDeque<>(getAvailableRaceNumbers(members, fieldName).unassigned());
        Set<Member> claimed = new HashSet<>();

        List<AllocationResult.Allocated> allocated = new ArrayList<>();
        List<AllocationResult.SkippedRequest> skipped = new ArrayList<>();

        for (AllocationRequest request : requests) {
            if (StringUtils.isBlank(request.licenseNumber())) {
                skipped.add(new AllocationResult.SkippedRequest(request, "No licence number provided"));
                continue;
            }
            Member member = byLicense.get(request.licenseNumber().trim().toUpperCase());
            if (member == null) {
                skipped.add(new AllocationResult.SkippedRequest(request,
                        "Licence number '" + request.licenseNumber() + "' not found in database"));
                continue;
            }
            if (!claimed.add(member)) {
                skipped.add(new AllocationResult.SkippedRequest(request,
                        "Duplicate request for this rider in this batch"));
                continue;
            }
            String existing = databaseService.getFieldValue(member, fieldName);
            if (StringUtils.isNotBlank(existing)) {
                skipped.add(new AllocationResult.SkippedRequest(request,
                        fieldName + " already assigned: " + existing));
                continue;
            }
            if (pool.isEmpty()) {
                skipped.add(new AllocationResult.SkippedRequest(request, "No available numbers remaining"));
                continue;
            }
            allocated.add(new AllocationResult.Allocated(request, member, pool.pollFirst()));
        }

        return new AllocationResult(allocated, skipped);
    }

    /**
     * Writes the proposed allocations from {@link #allocateRaceNumbers} to the member list.
     */
    public void applyAllocations(List<AllocationResult.Allocated> allocated, String fieldName) {
        for (AllocationResult.Allocated a : allocated) {
            databaseService.updateMemberField(a.member(), fieldName, String.valueOf(a.plateNumber()));
        }
    }

    /**
     * Imports registration data from a Cycling Ireland export file into the member list.
     *
     * Matching is attempted in priority order:
     *   1. By MID (internationalLicense) — most reliable, survives annual license changes
     *   2. By current licenseNumber — exact match
     *   3. By normalised full name + DOB — catches members previously added without a MID,
     *      and members whose licence number changed. Name matching strips all non-alpha
     *      characters and ignores case, so differences in whitespace, capitalisation, and
     *      punctuation (e.g. "O'Brien" vs "Obrien") are tolerated. DOB is compared with a
     *      ±5-day window to absorb common data-entry errors.
     *
     * On match: licenseNumber, licenseExpiry, and active are updated. If the matched DB
     * record had no internationalLicense (MID) set, it is populated from the import entry
     * so that future imports can match by MID instead of falling back to name+DOB.
     *
     * On no match: a new member row is added.
     */
    public ImportResult importRegistrationData(List<Member> members, List<RegistrationEntry> entries) {
        List<ImportResult.UpdatedEntry>  updated = new ArrayList<>();
        List<Member>                     added   = new ArrayList<>();
        List<ImportResult.SkippedEntry>  skipped = new ArrayList<>();

        MemberMatchingSession session = matcherFactory.createSession(members);

        for (RegistrationEntry entry : entries) {
            MatchCandidate candidate = new MatchCandidate(
                    entry.memberId(),
                    entry.licenseNumber(),
                    entry.firstName(),
                    entry.lastName(),
                    entry.dateOfBirth(),
                    entry.club());

            MatchResult result = session.match(candidate);

            if (result.ambiguous()) {
                skipped.add(new ImportResult.SkippedEntry(entry.firstName(), entry.lastName(),
                        entry.licenseNumber(),
                        "Ambiguous: name + DOB matched " + result.ambiguousCount() + " members"));
                continue;
            }

            if (result.found()) {
                Member match    = result.member();
                String oldLicense = match.getLicenseNumber();

                match.setLicenseNumber(entry.licenseNumber());
                match.setLicenseExpiry(entry.expiryDate());
                match.setActive("Yes");
                match.setClubName(entry.club());

                String riderCat = (entry.riderCategory() != null && !entry.riderCategory().isBlank())
                        ? entry.riderCategory() : entry.category();
                String licenseClass = mapLicenseClass(riderCat);
                if (licenseClass == null) licenseClass = licenseClassFromDob(entry.dateOfBirth());
                if (licenseClass != null) match.setLicenseClass(licenseClass);

                if (result.nameWasCorrected()) {
                    match.setGivenName(entry.firstName().trim());
                    match.setFamilyName(capitalizeName(normalizeSeparators(entry.lastName())));
                }

                // Backfill MID only when the DB record has none — an existing value is never
                // overwritten because CI data is not guaranteed to be more accurate than stored.
                String newMid = null;
                if (StringUtils.isBlank(match.getInternationalLicense())
                        && StringUtils.isNotBlank(entry.memberId())) {
                    newMid = entry.memberId().trim();
                    match.setInternationalLicense(newMid);
                }

                session.refreshIndices(match, entry.licenseNumber(), newMid);
                updated.add(new ImportResult.UpdatedEntry(match, oldLicense, entry.licenseNumber(),
                        result.matchMethod()));

            } else {
                // No match — add as new member. Unmatched RENEWALs are treated as new
                // registrations; duplicate-member validation will surface any collision.
                Member newMember = buildMember(entry, members.size() + added.size());
                added.add(newMember);
                session.refreshIndices(newMember,
                        newMember.getLicenseNumber(), newMember.getInternationalLicense());
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

    /** A single "new" rider row from a Sqorz Member Changes file. */
    public record SqorzNewRider(
            String licenseNumber, String firstName, String lastName,
            String gender, String birthDate, String club, String plate20) {}

    public record SqorzNewRidersResult(List<Member> added, List<SkippedRider> skipped) {
        public record SkippedRider(String licenseNumber, String reason) {}
    }

    /**
     * Adds brand-new riders reported in a Sqorz Member Changes file ("new" type rows)
     * to the member list. Unlike {@link #importRegistrationData}, there is no matching
     * step — these riders are not expected to already be in the database.
     *
     * License class is derived from date of birth, and license expiry from the licence
     * number's two-digit year prefix, consistent with how other imports derive these
     * fields. Country code defaults to "IRL". Date of birth is converted from Sqorz's
     * DD/MM/YYYY format to the database's required YYYY-MM-DD format.
     *
     * Plate 20 is only carried over when it is purely numeric. Clubs sometimes report
     * letter-prefixed codes (e.g. "LBMX13") for temporary club-racing plates — these are
     * not real database plate assignments and must never be written to the member record.
     *
     * A rider is skipped, rather than added, when: the licence number is blank; a member
     * with that licence number already exists; or both first and last name are blank.
     */
    public SqorzNewRidersResult addNewRidersFromSqorzChanges(List<Member> members, List<SqorzNewRider> riders) {
        Set<String> existingLicenses = members.stream()
                .map(Member::getLicenseNumber)
                .filter(StringUtils::isNotBlank)
                .map(l -> l.trim().toUpperCase())
                .collect(Collectors.toCollection(HashSet::new));

        List<Member> added = new ArrayList<>();
        List<SqorzNewRidersResult.SkippedRider> skipped = new ArrayList<>();

        for (SqorzNewRider rider : riders) {
            if (StringUtils.isBlank(rider.licenseNumber())) {
                skipped.add(new SqorzNewRidersResult.SkippedRider("(blank)", "No memberId provided"));
                continue;
            }
            if (StringUtils.isBlank(rider.firstName()) && StringUtils.isBlank(rider.lastName())) {
                skipped.add(new SqorzNewRidersResult.SkippedRider(rider.licenseNumber(), "Missing first and last name"));
                continue;
            }
            String key = rider.licenseNumber().trim().toUpperCase();
            if (!existingLicenses.add(key)) {
                skipped.add(new SqorzNewRidersResult.SkippedRider(rider.licenseNumber(),
                        "A member with this licence number already exists"));
                continue;
            }
            added.add(buildMemberFromSqorzNewRider(rider, members.size() + added.size()));
        }

        members.addAll(added);
        return new SqorzNewRidersResult(added, skipped);
    }

    private Member buildMemberFromSqorzNewRider(SqorzNewRider rider, int rowIndex) {
        Member m = new Member();
        m.setRowIndex(rowIndex);
        m.setLicenseNumber(rider.licenseNumber().trim());
        m.setGivenName(rider.firstName());
        m.setFamilyName(capitalizeName(normalizeSeparators(rider.lastName())));
        m.setGender(normalizeGender(rider.gender()));
        String birthDate = normalizeSqorzDate(rider.birthDate());
        m.setBirthDate(birthDate);
        m.setActive("Yes");
        m.setLicenseClass(licenseClassFromDob(birthDate));
        m.setLicenseExpiry(licenseExpiryFromLicenseNumber(rider.licenseNumber()));
        m.setLicenseCountryCode("IRL");
        m.setClubName(rider.club());
        if (StringUtils.isNotBlank(rider.plate20())) {
            String plate20 = rider.plate20().trim();
            if (isNumericPlate(plate20)) {
                m.setPlate20(plate20);
            } else {
                log.warn("Sqorz member changes: ignoring non-numeric Plate 20 '{}' for new rider '{}' " +
                        "(likely a temporary club-racing plate)", plate20, rider.licenseNumber());
            }
        }
        return m;
    }

    /**
     * Plates must be purely numeric. Clubs sometimes use letter-prefixed codes
     * (e.g. "LBMX13") for temporary club-racing plates — these must never be
     * written into the member database.
     */
    public static boolean isNumericPlate(String plate) {
        return plate != null && plate.matches("\\d+");
    }

    private static final DateTimeFormatter SQORZ_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Converts a Sqorz Member Changes date of birth (DD/MM/YYYY) to the database's
     * required YYYY-MM-DD format. Values already in YYYY-MM-DD, or that match neither
     * format, are returned unchanged so {@link ValidationService#validateDateFormats}
     * can flag them.
     */
    static String normalizeSqorzDate(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return trimmed;
        try {
            return LocalDate.parse(trimmed, SQORZ_DATE_FORMAT).toString();
        } catch (DateTimeParseException ignored) {
            return trimmed;
        }
    }

    /**
     * Derives a licence expiry date from a licence number's two-digit year prefix
     * (e.g. "26U100381" -> "2026-12-31"), matching the rule enforced by
     * {@link ValidationService#validateLicenseExpiryMatchesLicenseYear}.
     * Returns null if the licence number does not start with a two-digit year prefix.
     */
    static String licenseExpiryFromLicenseNumber(String licenseNumber) {
        if (licenseNumber == null) return null;
        String l = licenseNumber.trim();
        if (l.length() < 3) return null;
        if (!Character.isDigit(l.charAt(0)) || !Character.isDigit(l.charAt(1)) || Character.isDigit(l.charAt(2))) {
            return null;
        }
        int year = 2000 + Integer.parseInt(l.substring(0, 2));
        return year + "-12-31";
    }

    // ---- Private helpers ----

    private Member buildMember(RegistrationEntry entry, int rowIndex) {
        Member m = new Member();
        m.setRowIndex(rowIndex);
        m.setLicenseNumber(entry.licenseNumber());
        String riderCat = (entry.riderCategory() != null && !entry.riderCategory().isBlank())
                ? entry.riderCategory() : entry.category();
        String licenseClass = mapLicenseClass(riderCat);
        if (licenseClass == null) {
            licenseClass = licenseClassFromDob(entry.dateOfBirth());
        }
        m.setLicenseClass(licenseClass);
        m.setLicenseExpiry(entry.expiryDate());
        m.setGivenName(entry.firstName());
        m.setFamilyName(capitalizeName(normalizeSeparators(entry.lastName())));
        m.setBirthDate(entry.dateOfBirth());
        m.setGender(normalizeGender(entry.gender()));
        m.setActive("Yes");
        m.setClubName(entry.club());
        m.setEmail(entry.email());
        m.setLicenseCountryCode(entry.nationality());
        m.setEmergencyContactPerson(entry.emergencyContactName());
        m.setEmergencyContactNumber(entry.emergencyContactPhone());
        return m;
    }

    /**
     * Formats a family name for database output:
     * - Apostrophes and hyphens are replaced with spaces ("O'Brien" → "O BRIEN")
     * - Mc/Mac surname prefixes are separated from the rest ("McCann" → "MC CANN")
     * - Everything is uppercased
     */
    static String formatFamilyNameForOutput(String name) {
        if (name == null || name.isBlank()) return name;
        // Detect genuine Mc/Mac prefixes BEFORE uppercasing: a capital letter after the
        // prefix in the original name signals a real Gaelic prefix ("McCann" yes,
        // "Mackness" no). Insert a space so they separate correctly after uppercasing.
        String s = name.replaceAll("\\bMac([A-Z])", "Mac $1")
                       .replaceAll("\\bMc([A-Z])",  "Mc $1");
        // Replace all separator variants (apostrophe, backtick, quotes, curly quotes,
        // hyphens) with spaces, then uppercase and normalise whitespace
        return s.replaceAll("[''`\"\\u201C\\u201D\\u2018\\u2019\\-]", " ")
                .toUpperCase().replaceAll("\\s+", " ").trim();
    }

    /**
     * Capitalises each word in a name, treating spaces, hyphens, and apostrophes as
     * word boundaries. Input case is normalised first so "SMITH", "smith", and "sMiTh"
     * all produce "Smith". "O'BRIEN" → "O'Brien", "MAC-DONALD" → "Mac-Donald".
     */
    static String capitalizeName(String name) {
        if (name == null || name.isBlank()) return name;
        StringBuilder result = new StringBuilder(name.length());
        boolean capitalizeNext = true;
        for (char c : name.toLowerCase().toCharArray()) {
            if (c == ' ' || c == '-' || c == '\'') {
                result.append(c);
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Normalises a name for fuzzy matching by lowercasing and removing all
     * non-alphabetic characters (apostrophes, hyphens, spaces, etc.).
     * "O'Connell", "O Connell", and "OConnell" all normalise to "oconnell".
     */
    static String normalizeNameForMatch(String name) {
        return MemberMatchingSession.normalizeNameForMatch(name);
    }

    /**
     * Maps a Cycling Ireland rider category to "Adult" or "Youth".
     * JUNIOR, SENIOR, M40, M50, WM40 → Adult; U* categories → Youth.
     */
    /**
     * Derives "Adult" or "Youth" from date of birth.
     * A rider is Adult if their age in the current calendar year is 18 or over.
     * Returns null if the DOB is missing or cannot be parsed.
     */
    static String licenseClassFromDob(String dob) {
        if (dob == null || dob.isBlank()) return null;
        try {
            int birthYear = LocalDate.parse(dob.trim()).getYear();
            return (LocalDate.now().getYear() - birthYear) >= 18 ? "Adult" : "Youth";
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    static String mapLicenseClass(String riderCategory) {
        if (riderCategory == null || riderCategory.isBlank()) return null;
        return switch (riderCategory.trim().toUpperCase()) {
            case "JUNIOR", "SENIOR", "M40", "M50", "WM40" -> "Adult";
            default -> riderCategory.trim().toUpperCase().matches("U\\d+") ? "Youth" : null;
        };
    }

    private String normalizeGender(String gender) {
        if (gender == null) return null;
        return switch (gender.trim().toUpperCase()) {
            case "M", "MALE"   -> "M";
            case "F", "FEMALE" -> "F";
            default            -> gender.trim();
        };
    }

    /**
     * Normalises non-standard separator characters in a name to a standard apostrophe,
     * so that backtick, double-quote, and curly-quote variants are handled uniformly.
     */
    static String normalizeSeparators(String name) {
        if (name == null) return null;
        return name.replaceAll("[`\"\u201C\u201D\u2018\u2019]", "'");
    }

    static boolean dobsMatch(String dob1, String dob2) {
        return MemberMatchingSession.dobsMatch(dob1, dob2);
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
