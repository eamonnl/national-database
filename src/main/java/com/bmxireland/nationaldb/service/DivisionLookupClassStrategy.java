package com.bmxireland.nationaldb.service;

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
