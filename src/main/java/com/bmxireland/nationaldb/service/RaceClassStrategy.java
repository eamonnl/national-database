package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.BookingEntry;
import com.bmxireland.nationaldb.model.Member;

/**
 * Determines the Sqorz race class for a rider in a given event.
 *
 * <p>Implementations choose how class is determined — e.g. from age/DOB for
 * national series events, or from a club ability-based division for club events.
 * The resolved class is written into the Sqorz entry CSV.</p>
 */
public interface RaceClassStrategy {

    /**
     * @param sqorzClass      the class to write into the Sqorz CSV
     * @param supersededClass non-null when the strategy overrode a mismatched EventMaster
     *                        age group — the original EventMaster value, kept for warning output
     */
    record ClassResolution(String sqorzClass, String supersededClass) {}

    /**
     * Resolves the Sqorz class for one rider.
     *
     * @param entry  the raw booking entry from EventMaster
     * @param member the matched member record from the database
     * @return the resolved class and any mismatch information
     */
    ClassResolution resolve(BookingEntry entry, Member member);
}
