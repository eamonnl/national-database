package com.bmxireland.nationaldb.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.bmxireland.nationaldb.model.Member;

class MemberMatchingSessionTest {

    // ---- Helpers ----

    private Member member(String license, String mid, String given, String family,
                          String dob, String club) {
        Member m = new Member();
        m.setLicenseNumber(license);
        m.setInternationalLicense(mid);
        m.setGivenName(given);
        m.setFamilyName(family);
        m.setBirthDate(dob);
        m.setClubName(club);
        return m;
    }

    private MatchCandidate candidate(String mid, String license, String first, String last,
                                     String dob, String club) {
        return new MatchCandidate(mid, license, first, last, dob, club);
    }

    private MemberMatchingSession session(Member... members) {
        return new MemberMatchingSession(new ArrayList<>(List.of(members)));
    }

    // ---- Tier 1: MID ----

    @Test
    void matchByMid_exactMatch() {
        Member m = member("25U001", "MID-100", "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate("MID-100", "26U999", "X", "Y", "2000-01-01", null));

        assertTrue(r.found());
        assertEquals("MID", r.matchMethod());
        assertEquals(m, r.member());
    }

    @Test
    void matchByMid_leadingZeroVariant() {
        Member m = member("25U001", "289679", "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate("0289679", "26U999", "X", "Y", "2000-01-01", null));

        assertTrue(r.found());
        assertEquals("MID", r.matchMethod());
    }

    @Test
    void matchByMid_takesPriorityOverLicence() {
        Member byMid     = member("25U001", "MID-100", "Alice", "Smith", "1990-01-01", null);
        Member byLicence = member("26U001", null,      "Bob",   "Jones", "2000-01-01", null);
        // Candidate has a MID matching byMid AND a licence matching byLicence — MID wins.
        MatchResult r = session(byMid, byLicence).match(
                candidate("MID-100", "26U001", "X", "Y", "1990-01-01", null));

        assertEquals(byMid, r.member());
        assertEquals("MID", r.matchMethod());
    }

    // ---- Tier 2: Licence number ----

    @Test
    void matchByLicence_exactMatch() {
        Member m = member("26U001", null, "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate(null, "26U001", "X", "Y", "2000-01-01", null));

        assertTrue(r.found());
        assertEquals("licence number", r.matchMethod());
    }

    @Test
    void matchByLicence_caseInsensitive() {
        Member m = member("26u001", null, "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate(null, "26U001", "X", "Y", "2000-01-01", null));

        assertTrue(r.found());
    }

    // ---- Tier 3: Name + DOB ----

    @Test
    void matchByNameDob_exactNameAndDob() {
        Member m = member("25U999", null, "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate(null, "26U001", "Alice", "Smith", "1990-01-01", null));

        assertTrue(r.found());
        assertEquals("name + DOB", r.matchMethod());
        assertFalse(r.nameWasCorrected());
    }

    @Test
    void matchByNameDob_apostropheVariant() {
        Member m = member("25U001", null, "Jayden", "O'Connell", "2010-03-15", null);
        MatchResult r = session(m).match(candidate(null, "26U001", "Jayden", "O Connell", "2010-03-15", null));

        assertTrue(r.found());
        assertEquals("name + DOB", r.matchMethod());
    }

    @Test
    void matchByNameDob_singleTypoInName_corrected() {
        Member m = member("25U001", null, "Dermot", "Murphy", "1995-06-01", null);
        MatchResult r = session(m).match(candidate(null, "26U001", "Dermott", "Murphy", "1995-06-01", null));

        assertTrue(r.found());
        assertTrue(r.nameWasCorrected());
        assertTrue(r.matchMethod().contains("corrected"));
    }

    @Test
    void matchByNameDob_dobWithinTolerance() {
        Member m = member("25U999", null, "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate(null, "26U001", "Alice", "Smith", "1990-01-04", null));

        assertTrue(r.found());
    }

    @Test
    void matchByNameDob_dobBeyondTolerance_noMatch() {
        Member m = member("25U999", null, "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate(null, "26U001", "Alice", "Smith", "1990-02-01", null));

        assertFalse(r.found());
        assertFalse(r.ambiguous());
    }

    @Test
    void matchByNameDob_ambiguousWithoutClub() {
        Member a = member("25U001", null, "John", "Murphy", "2000-05-01", "Cork BMX");
        Member b = member("25U002", null, "John", "Murphy", "2000-05-01", "Lucan BMX");
        MatchResult r = session(a, b).match(candidate(null, "26U999", "John", "Murphy", "2000-05-01", null));

        assertTrue(r.ambiguous());
        assertEquals(2, r.ambiguousCount());
        assertNull(r.member());
    }

    // ---- Tier 3 + club tiebreaker ----

    @Test
    void matchByNameDobClub_breaksTie() {
        Member a = member("25U001", null, "John", "Murphy", "2000-05-01", "Cork BMX");
        Member b = member("25U002", null, "John", "Murphy", "2000-05-01", "Lucan BMX");
        MatchResult r = session(a, b).match(candidate(null, "26U999", "John", "Murphy", "2000-05-01", "Cork BMX"));

        assertTrue(r.found());
        assertEquals(a, r.member());
        assertEquals("name + DOB + club", r.matchMethod());
    }

    @Test
    void matchByNameDobClub_partialClubName_breaksTie() {
        Member a = member("25U001", null, "John", "Murphy", "2000-05-01", "Cork BMX Club");
        Member b = member("25U002", null, "John", "Murphy", "2000-05-01", "Lucan BMX");
        // Candidate says "Cork" — contained in "Cork BMX Club"
        MatchResult r = session(a, b).match(candidate(null, "26U999", "John", "Murphy", "2000-05-01", "Cork"));

        assertTrue(r.found());
        assertEquals(a, r.member());
    }

    @Test
    void matchByNameDobClub_stillAmbiguousIfClubMatchesNeither() {
        Member a = member("25U001", null, "John", "Murphy", "2000-05-01", "Cork BMX");
        Member b = member("25U002", null, "John", "Murphy", "2000-05-01", "Lucan BMX");
        MatchResult r = session(a, b).match(candidate(null, "26U999", "John", "Murphy", "2000-05-01", "Ratoath BMX"));

        assertTrue(r.ambiguous());
    }

    @Test
    void matchByNameDobClub_uniqueNameDob_clubNotRequired() {
        Member m = member("25U001", null, "Alice", "Smith", "1990-01-01", "Cork BMX");
        // Only one candidate — club is not consulted.
        MatchResult r = session(m).match(candidate(null, "26U001", "Alice", "Smith", "1990-01-01", "Lucan BMX"));

        assertTrue(r.found());
        assertEquals("name + DOB", r.matchMethod());
    }

    // ---- No match ----

    @Test
    void noMatch_returnsNoMatchResult() {
        Member m = member("25U001", "MID-1", "Alice", "Smith", "1990-01-01", null);
        MatchResult r = session(m).match(candidate("MID-999", "99U999", "Bob", "Jones", "2000-01-01", null));

        assertFalse(r.found());
        assertFalse(r.ambiguous());
        assertNull(r.matchMethod());
    }

    // ---- refreshIndices ----

    @Test
    void refreshIndices_newLicenceIsMatchableAfterRefresh() {
        Member m = member("25U001", null, "Alice", "Smith", "1990-01-01", null);
        MemberMatchingSession s = session(m);

        m.setLicenseNumber("26U001");
        s.refreshIndices(m, "26U001", null);

        MatchResult r = s.match(candidate(null, "26U001", "X", "Y", "2000-01-01", null));
        assertTrue(r.found());
        assertEquals("licence number", r.matchMethod());
    }

    @Test
    void refreshIndices_newMidIsMatchableAfterRefresh() {
        Member m = member("26U001", null, "Alice", "Smith", "1990-01-01", null);
        MemberMatchingSession s = session(m);

        m.setInternationalLicense("MID-NEW");
        s.refreshIndices(m, null, "MID-NEW");

        MatchResult r = s.match(candidate("MID-NEW", null, "X", "Y", "2000-01-01", null));
        assertTrue(r.found());
        assertEquals("MID", r.matchMethod());
    }

    // ---- Static utilities ----

    @Test
    void normalizeNameForMatch_stripsNonAlpha() {
        assertEquals("oconnell", MemberMatchingSession.normalizeNameForMatch("O'Connell"));
        assertEquals("oconnell", MemberMatchingSession.normalizeNameForMatch("O Connell"));
        assertEquals("oconnell", MemberMatchingSession.normalizeNameForMatch("OConnell"));
    }

    @Test
    void dobsMatch_exactAndTolerance() {
        assertTrue(MemberMatchingSession.dobsMatch("1990-01-01", "1990-01-01"));
        assertTrue(MemberMatchingSession.dobsMatch("1990-01-01", "1990-01-06")); // 5 days — on boundary
        assertFalse(MemberMatchingSession.dobsMatch("1990-01-01", "1990-01-07")); // 6 days — over
        assertFalse(MemberMatchingSession.dobsMatch(null, "1990-01-01"));
        assertFalse(MemberMatchingSession.dobsMatch("1990-01-01", ""));
    }
}
