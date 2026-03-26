package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the in-memory field access operations of DatabaseService.
 * File I/O (loadDatabase / saveDatabase) is not tested here as it requires
 * a real xlsx file; those operations are covered by integration testing.
 */
class DatabaseServiceTest {

    private DatabaseService databaseService;
    private Member member;

    @BeforeEach
    void setUp() {
        databaseService = new DatabaseService();
        member = new Member();
    }

    // ---- updateMemberField ----

    @Test
    void updateMemberField_licenseNumber_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "License Number", "LIC-001"));
        assertEquals("LIC-001", member.getLicenseNumber());
    }

    @Test
    void updateMemberField_licenseClass_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "License Class", "Adult"));
        assertEquals("Adult", member.getLicenseClass());
    }

    @Test
    void updateMemberField_givenName_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Given Name", "Alice"));
        assertEquals("Alice", member.getGivenName());
    }

    @Test
    void updateMemberField_familyName_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Family Name", "Smith"));
        assertEquals("Smith", member.getFamilyName());
    }

    @Test
    void updateMemberField_birthDate_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Birth Date", "1990-01-01"));
        assertEquals("1990-01-01", member.getBirthDate());
    }

    @Test
    void updateMemberField_plate20_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Plate 20", "101"));
        assertEquals("101", member.getPlate20());
    }

    @Test
    void updateMemberField_plate24_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Plate 24", "202"));
        assertEquals("202", member.getPlate24());
    }

    @Test
    void updateMemberField_plateRetro_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Plate Retro", "303"));
        assertEquals("303", member.getPlateRetro());
    }

    @Test
    void updateMemberField_plateOpen_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Plate Open", "404"));
        assertEquals("404", member.getPlateOpen());
    }

    @Test
    void updateMemberField_transponder20_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Transponder 20", "T-001"));
        assertEquals("T-001", member.getTransponder20());
    }

    @Test
    void updateMemberField_internationalLicense_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "International License", "UCI-999"));
        assertEquals("UCI-999", member.getInternationalLicense());
    }

    @Test
    void updateMemberField_email_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Email", "test@example.com"));
        assertEquals("test@example.com", member.getEmail());
    }

    @Test
    void updateMemberField_clubName_updatesField() {
        assertTrue(databaseService.updateMemberField(member, "Club Name", "Dublin BMX"));
        assertEquals("Dublin BMX", member.getClubName());
    }

    @Test
    void updateMemberField_unknownField_returnsFalse() {
        assertFalse(databaseService.updateMemberField(member, "nonexistent field", "value"));
    }

    @Test
    void updateMemberField_fieldNameIsCaseInsensitive() {
        assertTrue(databaseService.updateMemberField(member, "PLATE 20", "123"));
        assertEquals("123", member.getPlate20());
    }

    @Test
    void updateMemberField_fieldNameTrimsWhitespace() {
        assertTrue(databaseService.updateMemberField(member, "  Plate 20  ", "456"));
        assertEquals("456", member.getPlate20());
    }

    // ---- getFieldValue ----

    @Test
    void getFieldValue_licenseNumber_returnsValue() {
        member.setLicenseNumber("LIC-001");
        assertEquals("LIC-001", databaseService.getFieldValue(member, "License Number"));
    }

    @Test
    void getFieldValue_plate20_returnsValue() {
        member.setPlate20("101");
        assertEquals("101", databaseService.getFieldValue(member, "Plate 20"));
    }

    @Test
    void getFieldValue_internationalLicense_returnsValue() {
        member.setInternationalLicense("UCI-888");
        assertEquals("UCI-888", databaseService.getFieldValue(member, "International License"));
    }

    @Test
    void getFieldValue_unsetField_returnsNull() {
        assertNull(databaseService.getFieldValue(member, "Email"));
    }

    @Test
    void getFieldValue_unknownField_returnsNull() {
        assertNull(databaseService.getFieldValue(member, "nonexistent field"));
    }

    @Test
    void getFieldValue_roundtrip_matchesUpdate() {
        databaseService.updateMemberField(member, "Family Name", "O'Brien");
        assertEquals("O'Brien", databaseService.getFieldValue(member, "Family Name"));
    }

    // ---- normalizePhoneNumber ----

    @Test
    void phoneNumber_alreadyFormatted_unchanged() {
        assertEquals("083 1234567", databaseService.normalizePhoneNumber("083 1234567"));
    }

    @Test
    void phoneNumber_noSpaces_formatted() {
        assertEquals("083 1234567", databaseService.normalizePhoneNumber("0831234567"));
    }

    @Test
    void phoneNumber_internationalPlus353_stripped() {
        assertEquals("083 1234567", databaseService.normalizePhoneNumber("+353831234567"));
    }

    @Test
    void phoneNumber_international00353_stripped() {
        assertEquals("083 1234567", databaseService.normalizePhoneNumber("00353831234567"));
    }

    @Test
    void phoneNumber_spacesAndDashes_normalised() {
        assertEquals("083 1234567", databaseService.normalizePhoneNumber("083-123-4567"));
    }

    @Test
    void phoneNumber_internationalWithSpaces_normalised() {
        assertEquals("087 9876543", databaseService.normalizePhoneNumber("+353 87 987 6543"));
    }

    @Test
    void phoneNumber_null_returnsNull() {
        assertNull(databaseService.normalizePhoneNumber(null));
    }

    @Test
    void phoneNumber_blank_returnsBlank() {
        assertEquals("   ", databaseService.normalizePhoneNumber("   "));
    }

    // ---- resolveClubName ----

    private void setGroups(String... names) {
        databaseService.validGroupNames = new java.util.ArrayList<>(List.of(names));
    }

    @Test
    void resolveClubName_exactMatch_returned() {
        setGroups("Lucan BMX", "Dublin BMX");
        assertEquals("Lucan BMX", databaseService.resolveClubName("Lucan BMX"));
    }

    @Test
    void resolveClubName_caseInsensitiveExact_returned() {
        setGroups("Lucan BMX");
        assertEquals("Lucan BMX", databaseService.resolveClubName("lucan bmx"));
    }

    @Test
    void resolveClubName_containment_matchesLongerInput() {
        setGroups("Lucan BMX", "Dublin BMX");
        assertEquals("Lucan BMX", databaseService.resolveClubName("Lucan BMX Club"));
    }

    @Test
    void resolveClubName_containment_matchesShorterInput() {
        setGroups("Lucan BMX Club");
        assertEquals("Lucan BMX Club", databaseService.resolveClubName("Lucan BMX"));
    }

    @Test
    void resolveClubName_editDistance_closeMatch() {
        setGroups("Wexford BMX");
        assertEquals("Wexford BMX", databaseService.resolveClubName("Wexfrd BMX"));
    }

    @Test
    void resolveClubName_noMatch_returnsOther() {
        setGroups("Lucan BMX", "Dublin BMX");
        assertEquals("Other", databaseService.resolveClubName("Completely Unknown Club XYZZY"));
    }

    @Test
    void resolveClubName_noGroupsLoaded_returnsOriginal() {
        // validGroupNames empty — no workbook loaded
        assertEquals("Lucan BMX Club", databaseService.resolveClubName("Lucan BMX Club"));
    }

    @Test
    void resolveClubName_null_returnsNull() {
        setGroups("Lucan BMX");
        assertNull(databaseService.resolveClubName(null));
    }
}
