package com.bmxireland.nationaldb.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.bmxireland.nationaldb.model.BookingEntry;
import com.bmxireland.nationaldb.model.ClubDivision;
import com.bmxireland.nationaldb.model.Member;

/**
 * Ability-based class strategy that looks up each rider's division in a pre-loaded
 * name → division-code map (sourced from the club's ability-based division spreadsheet).
 *
 * <p>Name matching normalises both sides by stripping non-alphanumeric characters and
 * uppercasing, then tries the EventMaster booking name first, followed by the member
 * database name. Riders not found in the map fall back to {@link ClubDivision#D7} and
 * are recorded so a warning can be printed after the export.</p>
 *
 * <p>This class is instantiated per import run (not a Spring bean) so it can safely
 * accumulate per-run unmatched-rider state.</p>
 */
public class DivisionLookupClassStrategy implements RaceClassStrategy {

    private final Map<String, String> divisionMap;
    private final List<String> unmatchedRiders = new ArrayList<>();

    public DivisionLookupClassStrategy(Map<String, String> divisionMap) {
        this.divisionMap = divisionMap;
    }

    @Override
    public ClassResolution resolve(BookingEntry entry, Member member) {
        // Masters (over 29) always race in M1 (Rhythm Masters) regardless of club ability divisions
        if (isMaster(entry, member)) {
            return new ClassResolution(ClubDivision.M1.divisionName(), null);
        }

        // Try EventMaster name first, then member database name
        String division = lookupByName(entry.firstName(), entry.lastName());
        if (division == null && member != null) {
            division = lookupByName(member.getGivenName(), member.getFamilyName());
        }

        if (division != null) {
            String className = ClubDivision.fromCode(division)
                    .map(ClubDivision::divisionName)
                    .orElse(division);
            return new ClassResolution(className, null);
        }

        String displayName = (entry.firstName() + " " + entry.lastName()).trim();
        unmatchedRiders.add(displayName);
        return new ClassResolution(ClubDivision.D0.divisionName(), null);
    }

    /** Riders aged this many years or more (current year minus birth year) race Rhythm Masters. */
    private static final int MASTERS_MIN_AGE = 30;

    /**
     * A rider is a master if their age (current year minus birth year) is over 29. Ability-based
     * bookings carry no age group columns, so the date of birth is used first (booking, then
     * member record). Only when no DOB is available do we fall back to the booking's adult age
     * group, the member's licence class, or the CI rider category.
     */
    private static boolean isMaster(BookingEntry entry, Member member) {
        String dob = entry.dateOfBirth();
        if ((dob == null || dob.isBlank()) && member != null) dob = member.getBirthDate();
        if (dob != null && !dob.isBlank()) {
            try {
                return LocalDate.now().getYear() - LocalDate.parse(dob.trim()).getYear() >= MASTERS_MIN_AGE;
            } catch (DateTimeParseException ignored) {
                // fall through to the category-based checks
            }
        }
        if (entry.ageGroupAdult() != null && !entry.ageGroupAdult().isBlank()) return true;
        if (member != null && "Adult".equalsIgnoreCase(member.getLicenseClass())) return true;
        return "Adult".equals(MemberService.mapLicenseClass(entry.riderCategory()));
    }

    /** Returns the names of riders who were not found in the division map. */
    public List<String> getUnmatchedRiders() {
        return Collections.unmodifiableList(unmatchedRiders);
    }

    private String lookupByName(String first, String last) {
        if (first == null && last == null) return null;
        String key = AbilityDivisionLoader.normalizeName(
                (first == null ? "" : first) + (last == null ? "" : last));
        return divisionMap.get(key);
    }
}
