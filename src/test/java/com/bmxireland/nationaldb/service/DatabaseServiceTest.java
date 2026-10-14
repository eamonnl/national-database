package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
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

    // ---- load/save column alignment ----

    private static final String[] HEADERS = {
            "License Number", "License Class", "License Expiry", "Given Name", "Family Name",
            "Birth Date", "Gender", "Active", "Plate 20", "Plate 24", "Plate Retro", "Plate Open",
            "Transponder 20", "Transponder 24", "Transponder Retro", "Transponder Open",
            "Transponder Road", "License Country Code", "International License", "Club Name",
            "Team Name 1", "Team Name 2", "Team Name 3", "Team Name 4", "Team Name 5",
            "Email", "CC Email", "Emergency Contact Person", "Emergency Contact Number",
            "Suspension", "Suspension End Date" };

    @Test
    void saveDatabase_preservesColumnAlignment(@TempDir Path dir) throws Exception {
        File input = dir.resolve("MemberDatabase.xlsx").toFile();
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Members");
            sheet.createRow(0).createCell(0).setCellValue("****************");
            sheet.createRow(1).createCell(0).setCellValue("****************");
            Row header = sheet.createRow(2);
            for (int c = 0; c < HEADERS.length; c++) {
                header.createCell(c).setCellValue(HEADERS[c]);
            }
            Row data = sheet.createRow(3);
            data.createCell(0).setCellValue("LIC-1");
            data.createCell(4).setCellValue("Smith");
            data.createCell(16).setCellValue("GF-12345");
            data.createCell(17).setCellValue("IRL");
            data.createCell(18).setCellValue("10012345678");
            data.createCell(19).setCellValue("Lucan BMX");
            data.createCell(25).setCellValue("a@b.ie");
            data.createCell(27).setCellValue("Jane Smith");
            data.createCell(28).setCellValue("0871234567");
            data.createCell(29).setCellValue("Medical");
            data.createCell(30).setCellValue("2027-01-01");
            try (FileOutputStream out = new FileOutputStream(input)) {
                wb.write(out);
            }
        }
        ReflectionTestUtils.setField(databaseService, "databaseFilePath", input.getAbsolutePath());

        List<Member> members = databaseService.loadDatabase();
        assertEquals("IRL", members.get(0).getLicenseCountryCode());
        assertEquals("10012345678", members.get(0).getInternationalLicense());

        String saved = databaseService.saveDatabase(members);
        try (XSSFWorkbook wb = new XSSFWorkbook(new FileInputStream(saved))) {
            Row row = wb.getSheetAt(0).getRow(3);
            for (int c = 0; c < HEADERS.length; c++) {
                String expected = switch (c) {
                    case 0 -> "LIC-1";
                    case 4 -> "SMITH";
                    case 16 -> "GF-12345";
                    case 17 -> "IRL";
                    case 18 -> "10012345678";
                    case 19 -> "Lucan BMX";
                    case 25 -> "a@b.ie";
                    case 27 -> "Jane Smith";
                    case 28 -> "087 1234567";
                    case 29 -> "Medical";
                    case 30 -> "2027-01-01";
                    default -> "";
                };
                String actual = StringUtils.defaultString(databaseService.getCellStringValue(row.getCell(c)));
                if (c == 4) actual = actual.toUpperCase();
                assertEquals(expected, actual, "column " + c + " (" + HEADERS[c] + ")");
            }
        }
    }

    @Test
    void validateColumnHeaders_matchingHeaders_noWarnings() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Row header = wb.createSheet().createRow(0);
            for (int c = 0; c < HEADERS.length; c++) header.createCell(c).setCellValue(HEADERS[c]);
            assertTrue(databaseService.validateColumnHeaders(header).isEmpty());
        } catch (java.io.IOException e) {
            fail(e);
        }
    }

    @Test
    void validateColumnHeaders_missingColumn_warnsForShiftedColumns() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Row header = wb.createSheet().createRow(0);
            int out = 0;
            for (String h : HEADERS) {
                if (!h.equals("Transponder Road")) header.createCell(out++).setCellValue(h);
            }
            List<String> warnings = databaseService.validateColumnHeaders(header);
            assertFalse(warnings.isEmpty());
            assertTrue(warnings.get(0).contains("column 17: expected 'Transponder Road'"));
        } catch (java.io.IOException e) {
            fail(e);
        }
    }
}
