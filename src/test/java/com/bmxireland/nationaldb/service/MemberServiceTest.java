package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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

        var result = memberService.bulkUpdatePlate20(members, List.of("Alice Smith, 999"));

        assertEquals(1, result.applied().size());
        assertEquals(0, result.skipped().size());
        assertEquals("999", members.get(0).getPlate20());
        assertEquals("101", result.applied().get(0).oldValue());
        assertEquals("999", result.applied().get(0).newValue());
    }

    @Test
    void bulkUpdate_noMatch_isSkipped() {
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Unknown Person, 999"));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("No member found"));
    }

    @Test
    void bulkUpdate_ambiguousMatch_isSkipped() {
        // "Alice" matches both Alice Smith and Alice Johnson
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Alice, 999"));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("not unique"));
        assertEquals(2, result.skipped().get(0).matches().size());
    }

    @Test
    void bulkUpdate_missingComma_isSkipped() {
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Alice Smith 999"));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("Invalid format"));
    }

    @Test
    void bulkUpdate_emptyPlateNumber_isSkipped() {
        var result = memberService.bulkUpdatePlate20(sampleMembers(), List.of("Alice Smith, "));

        assertEquals(0, result.applied().size());
        assertEquals(1, result.skipped().size());
        assertTrue(result.skipped().get(0).reason().contains("empty"));
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
                List.of("Alice Smith, 201", "Unknown, 999", "Bob Jones, 202"));

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
}
