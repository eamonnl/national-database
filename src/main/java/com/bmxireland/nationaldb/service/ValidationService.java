package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service responsible for validating the member database.
 * Reports all validation issues found across the full member list.
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    /**
     * Represents a single validation issue.
     */
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

    /**
     * Runs all validations on the member list and returns the issues found.
     *
     * @param members the full member list
     * @return list of validation issues (empty if database is clean)
     */
    public List<ValidationIssue> validateAll(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();

        issues.addAll(validateNoDuplicateRaceNumbers(members));
        issues.addAll(validateNoDuplicateTransponderNumbers(members));
        issues.addAll(validateNoPossibleDuplicateMembers(members));
        issues.addAll(validateLicenseExpiryMatchesLicenseYear(members));
        issues.addAll(validateTransponderFormats(members));

        if (issues.isEmpty()) {
            log.info("Validation passed: no issues found.");
        } else {
            log.warn("Validation found {} issue(s).", issues.size());
        }

        return issues;
    }

    /**
     * Validates that no two members share the same race (plate) number within each category.
     * Ignores null, empty, and "None" values.
     */
    public List<ValidationIssue> validateNoDuplicateRaceNumbers(List<Member> members) {
        List<ValidationIssue> issues = new ArrayList<>();

        issues.addAll(findDuplicates(members, "Plate 20", Member::getPlate20));
        issues.addAll(findDuplicates(members, "Plate 24", Member::getPlate24));
        issues.addAll(findDuplicates(members, "Plate Retro", Member::getPlateRetro));
        issues.addAll(findDuplicates(members, "Plate Open", Member::getPlateOpen));

        return issues;
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

                // DOB must be non-empty and match exactly
                String dobA = StringUtils.trimToEmpty(a.getBirthDate());
                String dobB = StringUtils.trimToEmpty(b.getBirthDate());
                if (dobA.isEmpty() || !dobA.equals(dobB)) {
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
