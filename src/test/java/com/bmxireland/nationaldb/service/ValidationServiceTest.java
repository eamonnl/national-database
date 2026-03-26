package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ValidationServiceTest {

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new ValidationService();
    }

    // ---- Helpers ----

    private Member member(String licenseNumber, String givenName, String familyName,
                          String birthDate, String internationalLicense,
                          String plate20, String licenseExpiry) {
        Member m = new Member();
        m.setLicenseNumber(licenseNumber);
        m.setGivenName(givenName);
        m.setFamilyName(familyName);
        m.setBirthDate(birthDate);
        m.setInternationalLicense(internationalLicense);
        m.setPlate20(plate20);
        m.setLicenseExpiry(licenseExpiry);
        return m;
    }

    private Member withTransponder20(Member m, String value) {
        m.setTransponder20(value);
        return m;
    }

    // ---- validateNoDuplicateRaceNumbers ----

    @Test
    void duplicatePlate20_isDetected() {
        Member a = member("LIC001", "Alice", "Smith", "1990-01-01", null, "42", null);
        Member b = member("LIC002", "Bob",   "Jones", "1985-06-15", null, "42", null);

        var issues = validationService.validateNoDuplicateRaceNumbers(List.of(a, b));

        assertEquals(1, issues.size());
        assertEquals("DUPLICATE PLATE 20", issues.get(0).category());
        assertTrue(issues.get(0).affectedMembers().contains(a));
        assertTrue(issues.get(0).affectedMembers().contains(b));
    }

    @Test
    void duplicatePlate20_caseInsensitive() {
        Member a = member("LIC001", "Alice", "Smith", "1990-01-01", null, "abc", null);
        Member b = member("LIC002", "Bob",   "Jones", "1985-06-15", null, "ABC", null);

        var issues = validationService.validateNoDuplicateRaceNumbers(List.of(a, b));

        assertEquals(1, issues.size());
    }

    @Test
    void blankAndNonePlates_areIgnored() {
        Member a = member("LIC001", "Alice", "Smith", "1990-01-01", null, null,   null);
        Member b = member("LIC002", "Bob",   "Jones", "1985-06-15", null, "",     null);
        Member c = member("LIC003", "Carol", "Brown", "2000-03-10", null, "None", null);

        var issues = validationService.validateNoDuplicateRaceNumbers(List.of(a, b, c));

        assertTrue(issues.isEmpty());
    }

    @Test
    void uniquePlates_produceNoIssues() {
        Member a = member("LIC001", "Alice", "Smith", "1990-01-01", null, "101", null);
        Member b = member("LIC002", "Bob",   "Jones", "1985-06-15", null, "102", null);

        var issues = validationService.validateNoDuplicateRaceNumbers(List.of(a, b));

        assertTrue(issues.isEmpty());
    }

    // ---- validateNoDuplicateTransponderNumbers ----

    @Test
    void duplicateTransponder20_isDetected() {
        Member a = withTransponder20(member("LIC001", "Alice", "Smith", "1990-01-01", null, null, null), "T-999");
        Member b = withTransponder20(member("LIC002", "Bob",   "Jones", "1985-06-15", null, null, null), "T-999");

        var issues = validationService.validateNoDuplicateTransponderNumbers(List.of(a, b));

        assertEquals(1, issues.size());
        assertEquals("DUPLICATE TRANSPONDER 20", issues.get(0).category());
    }

    @Test
    void uniqueTransponders_produceNoIssues() {
        Member a = withTransponder20(member("LIC001", "Alice", "Smith", "1990-01-01", null, null, null), "T-001");
        Member b = withTransponder20(member("LIC002", "Bob",   "Jones", "1985-06-15", null, null, null), "T-002");

        var issues = validationService.validateNoDuplicateTransponderNumbers(List.of(a, b));

        assertTrue(issues.isEmpty());
    }

    // ---- validateNoPossibleDuplicateMembers ----

    @Test
    void sameNameAndDob_bothMissingIntlId_isFlagged() {
        Member a = member("LIC2023", "John", "Murphy", "2000-05-01", null, null, null);
        Member b = member("LIC2024", "John", "Murphy", "2000-05-01", null, null, null);

        var issues = validationService.validateNoPossibleDuplicateMembers(List.of(a, b));

        assertEquals(1, issues.size());
        assertEquals("POSSIBLE DUPLICATE MEMBER", issues.get(0).category());
    }

    @Test
    void similarNameAndSameDob_oneIntlIdMissing_isFlagged() {
        // "Jon" vs "John" — Levenshtein distance 1
        Member a = member("LIC2023", "Jon",  "Murphy", "2000-05-01", null,    null, null);
        Member b = member("LIC2024", "John", "Murphy", "2000-05-01", "IRL99", null, null);

        var issues = validationService.validateNoPossibleDuplicateMembers(List.of(a, b));

        assertEquals(1, issues.size());
    }

    @Test
    void sameNameAndDob_differentIntlIds_isNotFlagged() {
        Member a = member("LIC2023", "John", "Murphy", "2000-05-01", "IRL01", null, null);
        Member b = member("LIC2024", "John", "Murphy", "2000-05-01", "IRL02", null, null);

        var issues = validationService.validateNoPossibleDuplicateMembers(List.of(a, b));

        assertTrue(issues.isEmpty());
    }

    @Test
    void sameNameButDifferentDob_isNotFlagged() {
        Member a = member("LIC2023", "John", "Murphy", "2000-05-01", null, null, null);
        Member b = member("LIC2024", "John", "Murphy", "1995-08-20", null, null, null);

        var issues = validationService.validateNoPossibleDuplicateMembers(List.of(a, b));

        assertTrue(issues.isEmpty());
    }

    @Test
    void sameDobButVeryDifferentNames_isNotFlagged() {
        Member a = member("LIC2023", "Alice", "Smith",  "2000-05-01", null, null, null);
        Member b = member("LIC2024", "Bob",   "Jones",  "2000-05-01", null, null, null);

        var issues = validationService.validateNoPossibleDuplicateMembers(List.of(a, b));

        assertTrue(issues.isEmpty());
    }

    @Test
    void reversedNameOrder_isFlagged() {
        Member a = member("LIC2023", "Murphy", "John", "2000-05-01", null, null, null);
        Member b = member("LIC2024", "John",   "Murphy", "2000-05-01", null, null, null);

        var issues = validationService.validateNoPossibleDuplicateMembers(List.of(a, b));

        assertEquals(1, issues.size());
    }

    @Test
    void emptyMemberList_producesNoIssues() {
        assertTrue(validationService.validateAll(List.of()).isEmpty());
    }
}
