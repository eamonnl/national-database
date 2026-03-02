package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
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

    // ---- Private helpers ----

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
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value.trim());
    }
}
