package com.bmxireland.nationaldb.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.model.RegistrationEntry;

class MemberServiceTest {

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        // DatabaseService field access is pure in-memory — no file I/O, no Spring context needed.
        memberService = new MemberService(new DatabaseService());
    }

    // ---- Helpers ----

    private Member member(String licenseNumber, String givenName, String familyName,
                          String plate20, String licenseExpiry) {
        Member m = new Member();
        m.setLicenseNumber(licenseNumber);
        m.setGivenName(givenName);
        m.setFamilyName(familyName);
        m.setPlate20(plate20);
        m.setLicenseExpiry(licenseExpiry);
        return m;
    }

    private List<Member> sampleMembers() {
        return List.of(
                member("LIC001", "Alice", "Smith",   "101", "2025-12-31"),
                member("LIC002", "Bob",   "Jones",   "102", "2025-12-31"),
                member("LIC003", "Alice", "Johnson", "103", "2025-12-31")
        );
    }

    // ---- search ----

    @Test
    void search_byFullName_returnsMatch() {
        var results = memberService.search(sampleMembers(), "Alice Smith");
        assertEquals(1, results.size());
        assertEquals("LIC001", results.get(0).getLicenseNumber());
    }

    @Test
    void search_byGivenName_returnsAllMatches() {
        var results = memberService.search(sampleMembers(), "Alice");
        assertEquals(2, results.size());
    }

    @Test
    void search_byLicenseNumber_returnsMatch() {
        var results = memberService.search(sampleMembers(), "LIC002");
        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).getGivenName());
    }

    @Test
    void search_caseInsensitive() {
        var results = memberService.search(sampleMembers(), "alice smith");
        assertEquals(1, results.size());
    }

    @Test
    void search_noMatch_returnsEmpty() {
        var results = memberService.search(sampleMembers(), "Zzz");
        assertTrue(results.isEmpty());
    }

    // ---- bulkUpdatePlate20 ----

    @Test
    void bulkUpdate_uniqueMatch_appliesUpdate() {
        List<Member> members = List.of(
                member("LIC001", "Alice", "Smith", "101", "2025-12-31")
        );

        var result = memberService.bulkUpdatePlate20(members, List.of("Alice Smith  #999"));

        assertEquals(1, result.applied().size());
        assertEquals(0, result.skipped().size());
        assertEquals("999", members.get(0).getPlate20());
        assertEquals("101", result.applied().get(0).oldValue());
        assertEquals("999", result.applied().get(0).newValue());
    }

    @Test
    void bulkUpdate_noMatch_isSkipped() {
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Unknown Person #999"));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("No member found"));
    }

    @Test
    void bulkUpdate_ambiguousMatch_isSkipped() {
        // "Alice" matches both Alice Smith and Alice Johnson
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Alice #999"));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("not unique"));
        assertEquals(2, result.skipped().get(0).matches().size());
    }

    @Test
    void bulkUpdate_missingHash_isSkipped() {
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Alice Smith 999"));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("Invalid format"));
    }

    @Test
    void bulkUpdate_hashWithNoDigits_isSkipped() {
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Alice Smith #"));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("No plate number"));
    }

    @Test
    void bulkUpdate_trailingNotesAreIgnored() {
        List<Member> members = List.of(member("LIC001", "Alice", "Smith", "101", "2025-12-31"));

        var result = memberService.bulkUpdatePlate20(members,
                List.of("Alice Smith  #999  (need Full license details)"));

        assertEquals(1, result.applied().size());
        assertEquals("999", members.get(0).getPlate20());
    }

    @Test
    void bulkUpdate_blankLines_areIgnored() {
        List<String> lines = new ArrayList<>();
        lines.add("");
        lines.add("  ");
        lines.add(null);
        var result = memberService.bulkUpdatePlate20(sampleMembers(), lines);

        assertEquals(0, result.applied().size());
        assertEquals(0, result.skipped().size());
    }

    @Test
    void bulkUpdate_multipleEntries_processedIndependently() {
        List<Member> members = List.of(
                member("LIC001", "Alice", "Smith", "101", "2025-12-31"),
                member("LIC002", "Bob",   "Jones", "102", "2025-12-31")
        );

        var result = memberService.bulkUpdatePlate20(members,
                List.of("Alice Smith  #201", "Unknown #999", "Bob Jones  #202"));

        assertEquals(2, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertEquals("201", members.get(0).getPlate20());
        assertEquals("202", members.get(1).getPlate20());
    }

    // ---- getAvailableRaceNumbers ----

    @Test
    void availableNumbers_unassignedAbove100_areIncluded() {
        // No plates assigned above 100
        List<Member> members = List.of(
                member("LIC001", "Alice", "Smith", "50", "2025-12-31")
        );

        var result = memberService.getAvailableRaceNumbers(members, "Plate 20");

        assertTrue(result.unassigned().contains(101));
        assertTrue(result.unassigned().contains(120));
        assertTrue(result.reclaimable().isEmpty());
    }

    @Test
    void availableNumbers_activePlate_isNotReclaimable() {
        // License expires in the future — not stale
        List<Member> members = List.of(
                member("LIC001", "Alice", "Smith", "150", "2029-12-31")
        );

        var result = memberService.getAvailableRaceNumbers(members, "Plate 20");

        assertTrue(result.reclaimable().isEmpty());
        assertFalse(result.unassigned().contains(150)); // 150 is actively assigned
    }

    @Test
    void availableNumbers_stalePlate_isReclaimable() {
        // License expired more than 3 years ago
        List<Member> members = List.of(
                member("LIC001", "Alice", "Smith", "150", "2020-01-01")
        );

        var result = memberService.getAvailableRaceNumbers(members, "Plate 20");

        assertEquals(1, result.reclaimable().size());
        assertEquals(150, result.reclaimable().get(0).plateNumber());
    }

    @Test
    void availableNumbers_maxRangeIsAtLeast120() {
        List<Member> members = List.of();

        var result = memberService.getAvailableRaceNumbers(members, "Plate 20");

        assertTrue(result.maxRange() >= 120);
    }

    @Test
    void availableNumbers_maxRangeExtendsAboveHighestAssigned() {
        List<Member> members = List.of(
                member("LIC001", "Alice", "Smith", "200", "2029-12-31")
        );

        var result = memberService.getAvailableRaceNumbers(members, "Plate 20");

        assertTrue(result.maxRange() >= 200 + 20);
    }

    @Test
    void availableNumbers_nonNumericPlate_isSkipped() {
        List<Member> members = List.of(
                member("LIC001", "Alice", "Smith", "OPEN", "2029-12-31")
        );

        // Should not throw; non-numeric plate is silently skipped
        assertDoesNotThrow(() -> memberService.getAvailableRaceNumbers(members, "Plate 20"));
    }

    // ---- isLicenseStale ----

    @Test
    void isLicenseStale_expiredBeforeCutoff_returnsTrue() {
        Member m = member("LIC001", "Alice", "Smith", null, "2020-01-01");
        assertTrue(memberService.isLicenseStale(m, LocalDate.of(2023, 1, 1)));
    }

    @Test
    void isLicenseStale_expiredAfterCutoff_returnsFalse() {
        Member m = member("LIC001", "Alice", "Smith", null, "2024-01-01");
        assertFalse(memberService.isLicenseStale(m, LocalDate.of(2023, 1, 1)));
    }

    @Test
    void isLicenseStale_nullExpiry_returnsFalse() {
        Member m = member("LIC001", "Alice", "Smith", null, null);
        assertFalse(memberService.isLicenseStale(m, LocalDate.now()));
    }

    @Test
    void isLicenseStale_blankExpiry_returnsFalse() {
        Member m = member("LIC001", "Alice", "Smith", null, "   ");
        assertFalse(memberService.isLicenseStale(m, LocalDate.now()));
    }

    @Test
    void isLicenseStale_unparsableExpiry_returnsFalse() {
        Member m = member("LIC001", "Alice", "Smith", null, "not-a-date");
        assertFalse(memberService.isLicenseStale(m, LocalDate.now()));
    }

    // ---- importRegistrationData ----

    private RegistrationEntry entry(String licenseNumber, String memberId,
                                    String firstName, String lastName, String dob, String expiry) {
        return new RegistrationEntry(
                "Dublin BMX", "2026-01-01", expiry,
                licenseNumber, memberId, "LC_Adult",
                firstName, lastName, "test@example.com",
                dob, "MALE", "IRL", "Emergency Contact", "0851234567",
                "Adult");
    }

    private Member memberWithMid(String licenseNumber, String givenName, String familyName,
                                 String dob, String mid) {
        Member m = new Member();
        m.setLicenseNumber(licenseNumber);
        m.setGivenName(givenName);
        m.setFamilyName(familyName);
        m.setBirthDate(dob);
        m.setInternationalLicense(mid);
        return m;
    }

    @Test
    void import_matchByMid_updatesLicenseAndExpiry() {
        Member existing = memberWithMid("25U001", "Alice", "Smith", "1990-01-01", "MID-100");
        List<Member> members = new ArrayList<>(List.of(existing));
        var entries = List.of(entry("26U001", "MID-100", "Alice", "Smith", "1990-01-01", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(1, result.updated().size());
        assertEquals(0, result.added().size());
        assertEquals(0, result.skipped().size());
        assertEquals("26U001", existing.getLicenseNumber());
        assertEquals("2026-12-31", existing.getLicenseExpiry());
        assertEquals("Yes", existing.getActive());
        assertEquals("MID", result.updated().get(0).matchMethod());
    }

    @Test
    void import_matchByLicenseNumber_updatesExpiry() {
        Member existing = memberWithMid("26U001", "Alice", "Smith", "1990-01-01", null);
        List<Member> members = new ArrayList<>(List.of(existing));
        var entries = List.of(entry("26U001", "MID-200", "Alice", "Smith", "1990-01-01", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(1, result.updated().size());
        assertEquals("licence number", result.updated().get(0).matchMethod());
        // MID should be backfilled since it was empty
        assertEquals("MID-200", existing.getInternationalLicense());
    }

    @Test
    void import_matchByNameAndDob_fallback() {
        Member existing = memberWithMid("25U999", "Alice", "Smith", "1990-01-01", null);
        List<Member> members = new ArrayList<>(List.of(existing));
        // Different licence number, no MID — falls through to name+DOB
        var entries = List.of(entry("26U001", "MID-300", "Alice", "Smith", "1990-01-01", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(1, result.updated().size());
        assertEquals("name + DOB", result.updated().get(0).matchMethod());
        assertEquals("26U001", existing.getLicenseNumber());
        assertEquals("MID-300", existing.getInternationalLicense());
    }

    @Test
    void import_midWithLeadingZeros_matchesNumerically() {
        Member existing = memberWithMid("25U001", "Alice", "Smith", "1990-01-01", "289679");
        List<Member> members = new ArrayList<>(List.of(existing));
        var entries = List.of(entry("26U001", "0289679", "Alice", "Smith", "1990-01-01", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(1, result.updated().size());
        assertEquals("MID", result.updated().get(0).matchMethod());
    }

    @Test
    void import_newEntry_noMatch_addsMember() {
        List<Member> members = new ArrayList<>();
        var entries = List.of(entry("26U001", "MID-400", "Bob", "Jones", "2000-05-10", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(0, result.updated().size());
        assertEquals(1, result.added().size());
        assertEquals(0, result.skipped().size());
        assertEquals(1, members.size());
        Member added = members.get(0);
        assertEquals("Bob", added.getGivenName());
        assertEquals("Jones", added.getFamilyName());
        assertEquals("26U001", added.getLicenseNumber());
        assertEquals("MID-400", added.getInternationalLicense());
        assertEquals("M", added.getGender());
        assertEquals("Yes", added.getActive());
    }

    @Test
    void import_renewalNoMatch_isAddedAsNew() {
        List<Member> members = new ArrayList<>();
        var entries = List.of(entry("26U001", "MID-500", "Unknown", "Person", "1985-03-15", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(0, result.updated().size());
        assertEquals(1, result.added().size());
        assertEquals(0, result.skipped().size());
        assertEquals("Unknown", members.get(0).getGivenName());
    }

    @Test
    void import_ambiguousNameDobMatch_isSkipped() {
        // Two members with same name and DOB — ambiguous fallback
        Member a = memberWithMid("25U001", "John", "Murphy", "2000-05-01", null);
        Member b = memberWithMid("25U002", "John", "Murphy", "2000-05-01", null);
        List<Member> members = new ArrayList<>(List.of(a, b));
        var entries = List.of(entry("26U999", null, "John", "Murphy", "2000-05-01", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(0, result.updated().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("Ambiguous"));
    }

    @Test
    void import_apostropheInDbName_matchesQueryWithoutApostrophe() {
        Member existing = memberWithMid("25U001", "Jayden", "O'Connell", "2010-03-15", null);
        List<Member> members = new ArrayList<>(List.of(existing));
        // Registration file has "O Connell" (space instead of apostrophe)
        var entries = List.of(entry("26U001", "MID-600", "Jayden", "O Connell", "2010-03-15", "2026-12-31"));

        var result = memberService.importRegistrationData(members, entries);

        assertEquals(1, result.updated().size());
        assertEquals("26U001", existing.getLicenseNumber());
    }

    @Test
    void search_apostropheVariantsMatch() {
        Member m = new Member();
        m.setGivenName("Jayden");
        m.setFamilyName("O'Connell");

        // "O Connell" (space) should find "O'Connell" (apostrophe)
        assertFalse(memberService.search(List.of(m), "Jayden O Connell").isEmpty());
        // All-lowercase should also work
        assertFalse(memberService.search(List.of(m), "jayden oconnell").isEmpty());
    }

    @Test
    void mapLicenseClass_adultCategories_returnAdult() {
        for (String cat : List.of("SENIOR", "JUNIOR", "M40", "M50", "WM40")) {
            assertEquals("Adult", MemberService.mapLicenseClass(cat), "Expected Adult for: " + cat);
        }
    }

    @Test
    void mapLicenseClass_youthCategories_returnYouth() {
        for (String cat : List.of("U8", "U10", "U12", "U14", "U16", "U18")) {
            assertEquals("Youth", MemberService.mapLicenseClass(cat), "Expected Youth for: " + cat);
        }
    }

    @Test
    void mapLicenseClass_unknown_returnsNull() {
        assertNull(MemberService.mapLicenseClass("UNKNOWN"));
        assertNull(MemberService.mapLicenseClass(null));
    }

    @Test
    void import_familyName_isCapitalized() {
        List<Member> members = new ArrayList<>();
        var entries = List.of(entry("26U001", "MID-800", "Test", "MURPHY", "2010-01-01", "2026-12-31"));

        memberService.importRegistrationData(members, entries);

        assertEquals("Murphy", members.get(0).getFamilyName());
    }

    // ---- capitalizeName ----

    @Test
    void capitalizeName_allCaps_isLoweredAndCapitalized() {
        assertEquals("Smith", MemberService.capitalizeName("SMITH"));
    }

    @Test
    void capitalizeName_allLower_isCapitalized() {
        assertEquals("Smith", MemberService.capitalizeName("smith"));
    }

    @Test
    void capitalizeName_apostrophe_capitalizesBothParts() {
        assertEquals("O'Brien", MemberService.capitalizeName("O'BRIEN"));
    }

    @Test
    void capitalizeName_hyphen_capitalizesBothParts() {
        assertEquals("Mac-Donald", MemberService.capitalizeName("MAC-DONALD"));
    }

    @Test
    void capitalizeName_multiWord_capitalizesEachWord() {
        assertEquals("O Connell", MemberService.capitalizeName("O CONNELL"));
    }

    @Test
    void capitalizeName_null_returnsNull() {
        assertNull(MemberService.capitalizeName(null));
    }

    @Test
    void import_doesNotOverwriteExistingMid() {
        Member existing = memberWithMid("25U001", "Alice", "Smith", "1990-01-01", "EXISTING-MID");
        List<Member> members = new ArrayList<>(List.of(existing));
        var entries = List.of(entry("26U001", "DIFFERENT-MID", "Alice", "Smith", "1990-01-01", "2026-12-31"));

        memberService.importRegistrationData(members, entries);

        // MID matched — existing MID should not be overwritten
        assertEquals("EXISTING-MID", existing.getInternationalLicense());
    }
}
