package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Service responsible for validating the member database.
 * Reports all validation issues found across the full member list.
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    // Licences expiring more than this many years ago are considered stale for plate reclamation.
    private static final int STALE_RECLAIM_YEARS = 1;

    /** A single validation issue to be reported to the user. */
    public record ValidationIssue(String category, String description, List<Member> affectedMembers) {
        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  [%s] %s\n", category, description));
            for (Member m : affectedMembers) {
                sb.append(String.format("    - %s\n", m.toSummary()));
            }
            return sb.toString();
        }
    }

    /** A plate number automatically reclaimed from an expired licence holder. */
    public record ReclaimedPlate(String fieldName, String plateValue,
                                  Member activeHolder, Member staleMember) {}

    /** Combined result of a full validation run. */
    public record ValidationResult(List<ValidationIssue> issues, List<ReclaimedPlate> reclaimed) {}

    /** Result of a plate-duplicate check (issues to report + plates auto-reclaimed). */
    public record PlateCheckResult(List<ValidationIssue> issues, List<ReclaimedPlate> reclaimed) {}

    /**
     * Runs all validations on the member list.
     * Plate numbers shared between an active and a stale licence holder are automatically
     * reclaimed (the stale holder's plate is cleared) rather than reported as duplicates.
     */
    public ValidationResult validateAll(List<Member> members) {
        List<ValidationIssue> issues   = new ArrayList<>();
        List<ReclaimedPlate>  reclaimed = new ArrayList<>();

        PlateCheckResult plateCheck = validateNoDuplicateRaceNumbers(members);
        issues.addAll(plateCheck.issues());
        reclaimed.addAll(plateCheck.reclaimed());

        issues.addAll(validateRaceNumberRange(members));
        issues.addAll(validateNoDuplicateTransponderNumbers(members));
        issues.addAll(validateNoPossibleDuplicateMembers(members));
        issues.addAll(validateLicenseExpiryMatchesLicenseYear(members));
        issues.addAll(validateTransponderFormats(members));
        issues.addAll(validateDateFormats(members));

        if (issues.isEmpty() && reclaimed.isEmpty()) {
            log.info("Validation passed: no issues found.");
        } else {
            if (!reclaimed.isEmpty()) log.info("Auto-reclaimed {} plate(s).", reclaimed.size());
            if (!issues.isEmpty())    log.warn("Validation found {} issue(s).", issues.size());
        }

        return new ValidationResult(issues, reclaimed);
    }

    /**
     * Checks for duplicate race (plate) numbers across all plate fields.
     *
     * <ul>
     *   <li>When two members share a plate and one holds an expired (stale) licence while
     *       the other holds an active one, the plate is silently reclaimed from the stale
     *       holder — their plate field is cleared and a {@link ReclaimedPlate} is returned.</li>
     *   <li>When two members share a plate but one is Adult-class and the other is
     *       Youth-class the conflict is suppressed — plates are per-class in competition.</li>
     *   <li>All other duplicates are returned as {@link ValidationIssue} entries.</li>
     * </ul>
     */
    public PlateCheckResult validateNoDuplicateRaceNumbers(List<Member> members) {
        List<ValidationIssue> issues   = new ArrayList<>();
        List<ReclaimedPlate>  reclaimed = new ArrayList<>();
        LocalDate staleCutoff = LocalDate.now().minusYears(STALE_RECLAIM_YEARS);

        checkDuplicatePlates(members, "Plate 20",    Member::getPlate20,    Member::setPlate20,    issues, reclaimed, staleCutoff);
        checkDuplicatePlates(members, "Plate 24",    Member::getPlate24,    Member::setPlate24,    issues, reclaimed, staleCutoff);
        checkDuplicatePlates(members, "Plate Retro", Member::getPlateRetro, Member::setPlateRetro, issues, reclaimed, staleCutoff);
        checkDuplicatePlates(members, "Plate Open",  Member::getPlateOpen,  Member::setPlateOpen,  issues, reclaimed, staleCutoff);

        return new PlateCheckResult(issues, reclaimed);
    }

    /**
     * Validates that all numeric race (plate) numbers are three digits or fewer (1–999).
     * Numbers >= 1000 are invalid for BMX racing.
     */
    public List<ValidationIssue> validateRaceNumberRange(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();

        checkRaceNumberRange(members, "Plate 20",    Member::getPlate20,    issues);
        checkRaceNumberRange(members, "Plate 24",    Member::getPlate24,    issues);
        checkRaceNumberRange(members, "Plate Retro", Member::getPlateRetro, issues);
        checkRaceNumberRange(members, "Plate Open",  Member::getPlateOpen,  issues);

        return issues;
    }

    private void checkRaceNumberRange(List<Member> members, String fieldName,
                                      FieldExtractor extractor, List<ValidationIssue> issues) {
        for (Member m : members) {
            String value = extractor.extract(m);
            if (isBlankOrNone(value)) continue;
            try {
                int num = Integer.parseInt(value.trim());
                if (num >= 1000) {
                    issues.add(new ValidationIssue(
                            "INVALID RACE NUMBER",
                            String.format("%s value '%s' must be less than 1000 (three digits max)", fieldName, value.trim()),
                            List.of(m)));
                }
            } catch (NumberFormatException ignored) {
                // non-numeric values are handled elsewhere
            }
        }
    }

    /**
     * Validates that no two members share the same transponder number within each category.
     * Ignores null, empty, and "None" values.
     */
    public List<ValidationIssue> validateNoDuplicateTransponderNumbers(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();

        issues.addAll(findDuplicates(members, "Transponder 20", Member::getTransponder20));
        issues.addAll(findDuplicates(members, "Transponder 24", Member::getTransponder24));
        issues.addAll(findDuplicates(members, "Transponder Retro", Member::getTransponderRetro));
        issues.addAll(findDuplicates(members, "Transponder Open", Member::getTransponderOpen));

        return issues;
    }

    /**
     * Identifies possible duplicate member entries where the same person may have been
     * registered multiple times with different Cycling Ireland licence numbers.
     * This occurs when the international ID is missing, as it is the only stable identifier.
     *
     * A pair is flagged when:
     *   - DOB matches exactly (after trimming)
     *   - Full name is identical or differs by at most 2 characters (typo tolerance)
     *   - Both rows do NOT have different non-empty international licence IDs
     */
    public List<ValidationIssue> validateNoPossibleDuplicateMembers(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();

        for (int i = 0; i < members.size(); i++) {
            for (int j = i + 1; j < members.size(); j++) {
                Member a = members.get(i);
                Member b = members.get(j);

                // If both rows have different non-empty international IDs they are confirmed distinct people
                String intA = StringUtils.trimToEmpty(a.getInternationalLicense());
                String intB = StringUtils.trimToEmpty(b.getInternationalLicense());
                if (!intA.isEmpty() && !intB.isEmpty() && !intA.equalsIgnoreCase(intB)) {
                    continue;
                }

                // DOB must be non-empty and within tolerance
                String dobA = StringUtils.trimToEmpty(a.getBirthDate());
                String dobB = StringUtils.trimToEmpty(b.getBirthDate());
                if (!MemberService.dobsMatch(dobA, dobB)) {
                    continue;
                }

                // Names must be similar
                if (!namesAreSimilar(a, b)) {
                    continue;
                }

                String missingIdNote = buildMissingIdNote(intA, intB);
                String description = String.format(
                        "Possible duplicate: DOB '%s', similar name — different licence numbers%s",
                        dobA, missingIdNote);
                issues.add(new ValidationIssue("POSSIBLE DUPLICATE MEMBER", description, List.of(a, b)));
            }
        }

        return issues;
    }

    /**
     * Validates that members whose licence number begins with a two-digit year prefix
     * (e.g. "23U", "24U") have a licence expiry of 31 December of that year.
     * The prefix is considered a year indicator when the licence starts with two digits
     * followed by at least one non-digit character.
     */
    public List<ValidationIssue> validateLicenseExpiryMatchesLicenseYear(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();

        for (Member m : members) {
            String licence = StringUtils.trimToEmpty(m.getLicenseNumber());
            if (licence.length() < 3) continue;

            // Match a two-digit year prefix followed by a non-digit (e.g. "23U", "24S")
            if (!Character.isDigit(licence.charAt(0)) || !Character.isDigit(licence.charAt(1))
                    || Character.isDigit(licence.charAt(2))) continue;

            int year = 2000 + Integer.parseInt(licence.substring(0, 2));
            String expectedExpiry = year + "-12-31";
            String actualExpiry = StringUtils.trimToEmpty(m.getLicenseExpiry());

            if (!expectedExpiry.equals(actualExpiry)) {
                issues.add(new ValidationIssue(
                        "LICENSE EXPIRY MISMATCH",
                        String.format("Licence '%s' implies expiry %s but expiry is '%s'",
                                licence, expectedExpiry, actualExpiry.isEmpty() ? "(missing)" : actualExpiry),
                        List.of(m)));
            }
        }

        return issues;
    }

    private static final java.util.regex.Pattern DATE_FORMAT = java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    /**
     * Validates that non-blank date fields (Birth Date, License Expiry) are in YYYY-MM-DD format.
     */
    public List<ValidationIssue> validateDateFormats(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();
        for (Member m : members) {
            validateDateField(m, "Birth Date",      m.getBirthDate(),    issues);
            validateDateField(m, "License Expiry",  m.getLicenseExpiry(), issues);
        }
        return issues;
    }

    private void validateDateField(Member m, String fieldName, String value, List<ValidationIssue> issues) {
        if (isBlankOrNone(value)) return;
        if (!DATE_FORMAT.matcher(value.trim()).matches()) {
            issues.add(new ValidationIssue(
                    "INVALID DATE FORMAT",
                    String.format("%s value '%s' does not match required format YYYY-MM-DD", fieldName, value.trim()),
                    List.of(m)));
        }
    }

    private static final java.util.regex.Pattern TRANSPONDER_FORMAT = java.util.regex.Pattern.compile("^[A-Z]{2}-\\d{5}$");

    /**
     * Validates that all non-blank transponder numbers match the format "AA-NNNNN"
     * (two uppercase letters, a hyphen, five digits).
     */
    public List<ValidationIssue> validateTransponderFormats(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();

        validateTransponderField(members, "Transponder 20",    Member::getTransponder20,    issues);
        validateTransponderField(members, "Transponder 24",    Member::getTransponder24,    issues);
        validateTransponderField(members, "Transponder Retro", Member::getTransponderRetro, issues);
        validateTransponderField(members, "Transponder Open",  Member::getTransponderOpen,  issues);

        return issues;
    }

    private void validateTransponderField(List<Member> members, String fieldName,
                                          FieldExtractor extractor, List<ValidationIssue> issues) {
        for (Member m : members) {
            String value = extractor.extract(m);
            if (isBlankOrNone(value)) continue;
            if (!TRANSPONDER_FORMAT.matcher(value.trim()).matches()) {
                issues.add(new ValidationIssue(
                        "INVALID TRANSPONDER FORMAT",
                        String.format("%s value '%s' does not match required format AA-NNNNN", fieldName, value.trim()),
                        List.of(m)));
            }
        }
    }

    // ---- Private helpers ----

    private void checkDuplicatePlates(List<Member> members, String fieldName,
                                      FieldExtractor getter, BiConsumer<Member, String> setter,
                                      List<ValidationIssue> issues, List<ReclaimedPlate> reclaimed,
                                      LocalDate staleCutoff) {
        Map<String, List<Member>> valueMap = new LinkedHashMap<>();
        for (Member m : members) {
            String value = getter.extract(m);
            if (isBlankOrNone(value)) continue;
            valueMap.computeIfAbsent(value.trim().toUpperCase(), k -> new ArrayList<>()).add(m);
        }

        for (Map.Entry<String, List<Member>> entry : valueMap.entrySet()) {
            List<Member> holders = entry.getValue();
            if (holders.size() < 2) continue;

            String plateValue = entry.getKey();

            if (holders.size() == 2) {
                Member first  = holders.get(0);
                Member second = holders.get(1);

                // Suppress Adult vs Youth — plates are per-class in competition
                if (isAdultYouthPair(first, second)) continue;

                // Plates below 100 are protected lifetime plates — never auto-reclaim
                try {
                    if (Integer.parseInt(plateValue) < 100) {
                        issues.add(new ValidationIssue(
                                "DUPLICATE " + fieldName.toUpperCase(),
                                String.format("Value '%s' is shared by %d members:", plateValue, holders.size()),
                                holders));
                        continue;
                    }
                } catch (NumberFormatException ignored) {}

                // Auto-reclaim when one holder is active and the other is stale
                boolean firstActive  = hasKnownNonStaleExpiry(first,  staleCutoff);
                boolean secondActive = hasKnownNonStaleExpiry(second, staleCutoff);
                boolean firstStale   = isLicenceStale(first,  staleCutoff);
                boolean secondStale  = isLicenceStale(second, staleCutoff);

                if (firstActive && secondStale) {
                    setter.accept(second, null);
                    reclaimed.add(new ReclaimedPlate(fieldName, plateValue, first, second));
                    continue;
                }
                if (secondActive && firstStale) {
                    setter.accept(first, null);
                    reclaimed.add(new ReclaimedPlate(fieldName, plateValue, second, first));
                    continue;
                }
            }

            issues.add(new ValidationIssue(
                    "DUPLICATE " + fieldName.toUpperCase(),
                    String.format("Value '%s' is shared by %d members:", plateValue, holders.size()),
                    holders));
        }
    }

    private boolean isAdultYouthPair(Member a, Member b) {
        String classA = StringUtils.trimToEmpty(a.getLicenseClass()).toLowerCase();
        String classB = StringUtils.trimToEmpty(b.getLicenseClass()).toLowerCase();
        return (classA.equals("adult") && classB.equals("youth"))
                || (classA.equals("youth") && classB.equals("adult"));
    }

    private boolean isLicenceStale(Member m, LocalDate cutoff) {
        String expiry = StringUtils.trimToEmpty(m.getLicenseExpiry());
        if (expiry.isEmpty()) return false;
        try {
            return LocalDate.parse(expiry).isBefore(cutoff);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean hasKnownNonStaleExpiry(Member m, LocalDate cutoff) {
        String expiry = StringUtils.trimToEmpty(m.getLicenseExpiry());
        if (expiry.isEmpty()) return false;
        try {
            return !LocalDate.parse(expiry).isBefore(cutoff);
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean namesAreSimilar(Member a, Member b) {
        String fullA = normalizeName(a.getGivenName(), a.getFamilyName());
        String fullB = normalizeName(b.getGivenName(), b.getFamilyName());
        String reversedA = normalizeName(a.getFamilyName(), a.getGivenName());

        if (fullA.isEmpty() || fullB.isEmpty()) {
            return false;
        }
        if (fullA.equals(fullB) || reversedA.equals(fullB)) {
            return true;
        }
        // Allow up to 2 character edits to catch minor typos
        return LevenshteinDistance.getDefaultInstance().apply(fullA, fullB) <= 2;
    }

    private String normalizeName(String first, String second) {
        String combined = StringUtils.defaultString(first) + " " + StringUtils.defaultString(second);
        return StringUtils.normalizeSpace(combined).toLowerCase();
    }

    private String buildMissingIdNote(String intA, String intB) {
        if (intA.isEmpty() && intB.isEmpty()) {
            return " (both rows missing international ID)";
        } else if (intA.isEmpty()) {
            return " (first row missing international ID)";
        } else if (intB.isEmpty()) {
            return " (second row missing international ID)";
        }
        return "";
    }




    @FunctionalInterface
    private interface FieldExtractor {
        String extract(Member member);
    }

    /**
     * Finds members sharing the same non-empty value for a given field.
     */
    private List<ValidationIssue> findDuplicates(List<Member> members, String fieldName, FieldExtractor extractor) {
        Map<String, List<Member>> valueMap = new LinkedHashMap<>();

        for (Member member : members) {
            String value = extractor.extract(member);
            if (isBlankOrNone(value)) {
                continue;
            }
            valueMap.computeIfAbsent(value.trim().toUpperCase(), k -> new ArrayList<>()).add(member);
        }

        List<ValidationIssue> issues = new ArrayList<>();
        for (Map.Entry<String, List<Member>> entry : valueMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                issues.add(new ValidationIssue(
                        "DUPLICATE " + fieldName.toUpperCase(),
                        String.format("Value '%s' is shared by %d members:", entry.getKey(), entry.getValue().size()),
                        entry.getValue()
                ));
            }
        }

        return issues;
    }

    private boolean isBlankOrNone(String value) {
        return StringUtils.isBlank(value) || "none".equalsIgnoreCase(StringUtils.trim(value));
    }
}
