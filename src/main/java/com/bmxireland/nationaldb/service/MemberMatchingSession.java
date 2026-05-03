package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stateful, per-import matching session that identifies existing members for an incoming
 * record using a three-tier strategy:
 *
 * <ol>
 *   <li>International licence number (UCI / CI MID) — exact, with numeric leading-zero tolerance</li>
 *   <li>Domestic licence number — exact, case-insensitive</li>
 *   <li>Normalised full name + DOB — Levenshtein distance ≤ 1 on stripped name, ±5-day DOB window.
 *       When multiple candidates survive name+DOB, club name is used as a tiebreaker.</li>
 * </ol>
 *
 * <p>Each session owns its own indices. Call {@link #refreshIndices} after mutating a matched
 * member so that subsequent entries in the same file can locate it under its new identifiers.</p>
 *
 * <p>Not thread-safe. Construct one session per import run via {@link MemberMatcherFactory}.</p>
 */
public final class MemberMatchingSession {

    static final int DOB_TOLERANCE_DAYS  = 5;
    static final int NAME_FUZZY_DISTANCE = 1;
    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();

    private final List<Member> members;
    private final Map<String, Member> byMid;
    private final Map<String, Member> byLicense;

    MemberMatchingSession(List<Member> members) {
        this.members    = members;
        this.byMid      = new HashMap<>();
        this.byLicense  = new HashMap<>();
        for (Member m : members) {
            if (StringUtils.isNotBlank(m.getInternationalLicense())) {
                byMid.put(m.getInternationalLicense().trim(), m);
            }
            if (StringUtils.isNotBlank(m.getLicenseNumber())) {
                byLicense.put(m.getLicenseNumber().trim().toUpperCase(), m);
            }
        }
    }

    /**
     * Attempts to match {@code candidate} against the member list, trying each tier in order.
     */
    public MatchResult match(MatchCandidate candidate) {
        if (StringUtils.isNotBlank(candidate.mid())) {
            Member m = findByMid(candidate.mid().trim());
            if (m != null) return MatchResult.of(m, "MID", false);
        }

        if (StringUtils.isNotBlank(candidate.licenseNumber())) {
            Member m = byLicense.get(candidate.licenseNumber().trim().toUpperCase());
            if (m != null) return MatchResult.of(m, "licence number", false);
        }

        return matchByNameAndDob(candidate);
    }

    /**
     * Updates the internal indices after a matched member's identifiers have been changed.
     * Pass {@code null} for either argument if it was not updated.
     */
    public void refreshIndices(Member member, String newLicense, String newMid) {
        if (StringUtils.isNotBlank(newLicense)) {
            byLicense.put(newLicense.trim().toUpperCase(), member);
        }
        if (StringUtils.isNotBlank(newMid)) {
            byMid.put(newMid.trim(), member);
        }
    }

    // ---- Private matching tiers ----

    private MatchResult matchByNameAndDob(MatchCandidate candidate) {
        String normalizedName = normalizeNameForMatch(
                StringUtils.defaultString(candidate.firstName()) + " " +
                StringUtils.defaultString(candidate.lastName()));

        List<Member> nameMatches = members.stream()
                .filter(m -> LEVENSHTEIN.apply(
                        normalizeNameForMatch(
                                StringUtils.defaultString(m.getGivenName()) + " " +
                                StringUtils.defaultString(m.getFamilyName())),
                        normalizedName) <= NAME_FUZZY_DISTANCE)
                .collect(Collectors.toList());

        List<Member> dobMatches = nameMatches.stream()
                .filter(m -> dobsMatch(candidate.dateOfBirth(), m.getBirthDate()))
                .collect(Collectors.toList());

        if (dobMatches.size() == 1) {
            return makeNameDobResult(dobMatches.get(0), normalizedName, "DOB");
        }

        if (dobMatches.size() > 1 && StringUtils.isNotBlank(candidate.clubName())) {
            // Try club name as tiebreaker: use normalised containment matching
            String normalizedClub = normalizeNameForMatch(candidate.clubName());
            List<Member> clubMatches = dobMatches.stream()
                    .filter(m -> {
                        String mc = normalizeNameForMatch(StringUtils.defaultString(m.getClubName()));
                        return !mc.isEmpty() &&
                               (mc.contains(normalizedClub) || normalizedClub.contains(mc));
                    })
                    .collect(Collectors.toList());
            if (clubMatches.size() == 1) {
                return makeNameDobResult(clubMatches.get(0), normalizedName, "DOB + club");
            }
        }

        if (dobMatches.size() > 1) {
            return MatchResult.ambiguous(dobMatches.size());
        }

        return MatchResult.noMatch();
    }

    private MatchResult makeNameDobResult(Member match, String normalizedIncoming, String suffix) {
        String normalizedStored = normalizeNameForMatch(
                StringUtils.defaultString(match.getGivenName()) + " " +
                StringUtils.defaultString(match.getFamilyName()));
        boolean corrected = !normalizedStored.equals(normalizedIncoming);
        String method = (corrected ? "name (corrected) + " : "name + ") + suffix;
        return MatchResult.of(match, method, corrected);
    }

    private Member findByMid(String mid) {
        Member exact = byMid.get(mid);
        if (exact != null) return exact;
        // Numeric comparison absorbs leading-zero differences (e.g. "0289679" vs "289679")
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

    // ---- Package-private static utilities (delegates kept on MemberService for callers) ----

    static String normalizeNameForMatch(String name) {
        if (name == null) return "";
        return name.toLowerCase().replaceAll("[^a-z]", "");
    }

    static boolean dobsMatch(String dob1, String dob2) {
        if (dob1 == null || dob1.isEmpty() || dob2 == null || dob2.isEmpty()) return false;
        if (dob1.equalsIgnoreCase(dob2)) return true;
        try {
            long diff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(dob1), LocalDate.parse(dob2)));
            return diff <= DOB_TOLERANCE_DAYS;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
