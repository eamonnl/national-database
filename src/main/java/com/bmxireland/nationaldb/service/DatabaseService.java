package com.bmxireland.nationaldb.service;

import com.bmxireland.nationaldb.model.Member;
import com.bmxireland.nationaldb.model.RegistrationEntry;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for reading and writing the member database xlsx file.
 * Preserves the header section delimited by '****************' rows.
 */
@Service
public class DatabaseService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);
    private static final String HEADER_DELIMITER = "****************";
    private static final int EXPECTED_COLUMNS = 28;

    @Value("${database.file.path:MemberDatabase.xlsx}")
    private String databaseFilePath;

    private List<Row> headerRows; // preserved raw header rows (before second delimiter)
    private Row columnHeaderRow;  // the column names row (immediately after second delimiter)
    private Workbook workbook;

    /**
     * Loads members from the xlsx file, validating the header structure.
     *
     * @return list of Member objects parsed from the spreadsheet
     * @throws IOException if the file cannot be read or has invalid structure
     */
    public List<Member> loadDatabase() throws IOException {
        File file = new File(databaseFilePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Database file not found: " + file.getAbsolutePath());
        }

        log.info("Loading database from: {}", file.getAbsolutePath());

        try (FileInputStream fis = new FileInputStream(file)) {
            workbook = new XSSFWorkbook(fis);
        }

        Sheet sheet = workbook.getSheetAt(0);
        List<Member> members = new ArrayList<>();

        // Parse header: find the two '****************' delimiter rows
        int firstDelimiterRow = -1;
        int secondDelimiterRow = -1;
        headerRows = new ArrayList<>();

        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                String firstCell = getCellStringValue(row.getCell(0));
                if (HEADER_DELIMITER.equals(firstCell)) {
                    if (firstDelimiterRow == -1) {
                        firstDelimiterRow = i;
                    } else {
                        secondDelimiterRow = i;
                        break;
                    }
                }
            }
        }

        if (firstDelimiterRow == -1 || secondDelimiterRow == -1) {
            throw new IOException("Invalid database format: could not find header delimiters ('" +
                    HEADER_DELIMITER + "'). The file must contain two rows starting with '" +
                    HEADER_DELIMITER + "' to delimit the header section.");
        }

        log.info("Header delimiters found at rows {} and {}", firstDelimiterRow + 1, secondDelimiterRow + 1);

        // Store header rows (inclusive of both delimiters)
        for (int i = firstDelimiterRow; i <= secondDelimiterRow; i++) {
            headerRows.add(sheet.getRow(i));
        }

        // The column header row is immediately after the second delimiter
        int columnHeaderIndex = secondDelimiterRow + 1;
        columnHeaderRow = sheet.getRow(columnHeaderIndex);
        if (columnHeaderRow == null) {
            throw new IOException("No column header row found after the header section.");
        }

        log.info("Column headers at row {}: {}", columnHeaderIndex + 1,
                getCellStringValue(columnHeaderRow.getCell(0)));

        // Parse data rows (starting after column header row)
        int dataStartRow = columnHeaderIndex + 1;
        int memberIndex = 0;
        for (int i = dataStartRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isRowEmpty(row)) {
                continue;
            }

            Member member = parseRow(row, memberIndex);
            if (StringUtils.isNotBlank(member.getLicenseNumber())) {
                members.add(member);
                memberIndex++;
            }
        }

        log.info("Loaded {} members from database", members.size());
        return members;
    }

    /**
     * Saves the member list back to the xlsx file, preserving the header section.
     *
     * @param members the list of members to write
     * @throws IOException if the file cannot be written
     */
    public void saveDatabase(List<Member> members) throws IOException {
        File file = new File(databaseFilePath);

        // Create a backup before writing
        if (file.exists()) {
            String backupName = databaseFilePath.replace(".xlsx",
                    "_backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx");
            Files.copy(file.toPath(), Path.of(backupName), StandardCopyOption.REPLACE_EXISTING);
            log.info("Backup created: {}", backupName);
        }

        Workbook newWorkbook = new XSSFWorkbook();
        Sheet newSheet = newWorkbook.createSheet("Members");

        // Write header rows (preserved from original file)
        int currentRow = 0;
        for (Row headerRow : headerRows) {
            Row newRow = newSheet.createRow(currentRow);
            copyRow(headerRow, newRow);
            currentRow++;
        }

        // Write column header row
        Row newColumnHeader = newSheet.createRow(currentRow);
        copyRow(columnHeaderRow, newColumnHeader);
        currentRow++;

        // Write member data rows
        for (Member member : members) {
            Row newRow = newSheet.createRow(currentRow);
            writeMemberToRow(member, newRow);
            currentRow++;
        }

        // Write to file
        try (FileOutputStream fos = new FileOutputStream(file)) {
            newWorkbook.write(fos);
        }
        newWorkbook.close();

        log.info("Database saved with {} members to: {}", members.size(), file.getAbsolutePath());
    }

    /**
     * Reads a Cycling Ireland registration export xlsx file and returns its rows as
     * {@link RegistrationEntry} objects. Expects the column layout produced by the
     * standard "RegisteredCyclistData" export (header row 0, data from row 1).
     *
     * @param filePath path to the registration xlsx file
     * @return list of registration entries (header row excluded)
     * @throws IOException if the file cannot be read
     */
    public List<RegistrationEntry> loadRegistrationFile(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Registration file not found: " + file.getAbsolutePath());
        }

        log.info("Loading registration file from: {}", file.getAbsolutePath());

        List<RegistrationEntry> entries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {   // row 0 is the header
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                entries.add(new RegistrationEntry(
                        getCellStringValue(row.getCell(0)),   // Club
                        getCellStringValue(row.getCell(1)),   // Registration Date
                        getCellStringValue(row.getCell(2)),   // Expiry Date
                        getCellStringValue(row.getCell(3)),   // New or Renewal
                        getCellStringValue(row.getCell(4)),   // Licence Number
                        getCellStringValue(row.getCell(5)),   // MID (stable member ID)
                        getCellStringValue(row.getCell(7)),   // Category
                        getCellStringValue(row.getCell(10)),  // First Name
                        getCellStringValue(row.getCell(11)),  // Last Name
                        getCellStringValue(row.getCell(12)),  // Email
                        getCellStringValue(row.getCell(13)),  // DOB
                        getCellStringValue(row.getCell(14)),  // Gender
                        getCellStringValue(row.getCell(21)),  // Nationality
                        getCellStringValue(row.getCell(24)),  // Emergency Contact Name
                        getCellStringValue(row.getCell(26))   // Emergency Contact Phone
                ));
            }
        }

        log.info("Loaded {} registration entries", entries.size());
        return entries;
    }

    /**
     * Updates a single member field value given the field name.
     *
     * @param member    the member to update
     * @param fieldName the field name (matching column header)
     * @param newValue  the new value to set
     * @return true if the field was recognized and updated
     */
    public boolean updateMemberField(Member member, String fieldName, String newValue) {
        switch (fieldName.toLowerCase().trim()) {
            case "license number" -> member.setLicenseNumber(newValue);
            case "license class" -> member.setLicenseClass(newValue);
            case "license expiry" -> member.setLicenseExpiry(newValue);
            case "given name" -> member.setGivenName(newValue);
            case "family name" -> member.setFamilyName(newValue);
            case "birth date" -> member.setBirthDate(newValue);
            case "gender" -> member.setGender(newValue);
            case "active" -> member.setActive(newValue);
            case "plate 20" -> member.setPlate20(newValue);
            case "plate 24" -> member.setPlate24(newValue);
            case "plate retro" -> member.setPlateRetro(newValue);
            case "plate open" -> member.setPlateOpen(newValue);
            case "transponder 20" -> member.setTransponder20(newValue);
            case "transponder 24" -> member.setTransponder24(newValue);
            case "transponder retro" -> member.setTransponderRetro(newValue);
            case "transponder open" -> member.setTransponderOpen(newValue);
            case "license country code" -> member.setLicenseCountryCode(newValue);
            case "international license" -> member.setInternationalLicense(newValue);
            case "club name" -> member.setClubName(newValue);
            case "team name 1" -> member.setTeamName1(newValue);
            case "team name 2" -> member.setTeamName2(newValue);
            case "team name 3" -> member.setTeamName3(newValue);
            case "team name 4" -> member.setTeamName4(newValue);
            case "team name 5" -> member.setTeamName5(newValue);
            case "email" -> member.setEmail(newValue);
            case "cc email" -> member.setCcEmail(newValue);
            case "emergency contact person" -> member.setEmergencyContactPerson(newValue);
            case "emergency contact number" -> member.setEmergencyContactNumber(newValue);
            default -> {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the ordered list of editable field names (matching column headers).
     */
    public List<String> getFieldNames() {
        return List.of(
                "License Number", "License Class", "License Expiry",
                "Given Name", "Family Name", "Birth Date", "Gender", "Active",
                "Plate 20", "Plate 24", "Plate Retro", "Plate Open",
                "Transponder 20", "Transponder 24", "Transponder Retro", "Transponder Open",
                "License Country Code", "International License",
                "Club Name",
                "Team Name 1", "Team Name 2", "Team Name 3", "Team Name 4", "Team Name 5",
                "Email", "CC Email",
                "Emergency Contact Person", "Emergency Contact Number"
        );
    }

    /**
     * Returns the value of a field by name.
     */
    public String getFieldValue(Member member, String fieldName) {
        return switch (fieldName.toLowerCase().trim()) {
            case "license number" -> member.getLicenseNumber();
            case "license class" -> member.getLicenseClass();
            case "license expiry" -> member.getLicenseExpiry();
            case "given name" -> member.getGivenName();
            case "family name" -> member.getFamilyName();
            case "birth date" -> member.getBirthDate();
            case "gender" -> member.getGender();
            case "active" -> member.getActive();
            case "plate 20" -> member.getPlate20();
            case "plate 24" -> member.getPlate24();
            case "plate retro" -> member.getPlateRetro();
            case "plate open" -> member.getPlateOpen();
            case "transponder 20" -> member.getTransponder20();
            case "transponder 24" -> member.getTransponder24();
            case "transponder retro" -> member.getTransponderRetro();
            case "transponder open" -> member.getTransponderOpen();
            case "license country code" -> member.getLicenseCountryCode();
            case "international license" -> member.getInternationalLicense();
            case "club name" -> member.getClubName();
            case "team name 1" -> member.getTeamName1();
            case "team name 2" -> member.getTeamName2();
            case "team name 3" -> member.getTeamName3();
            case "team name 4" -> member.getTeamName4();
            case "team name 5" -> member.getTeamName5();
            case "email" -> member.getEmail();
            case "cc email" -> member.getCcEmail();
            case "emergency contact person" -> member.getEmergencyContactPerson();
            case "emergency contact number" -> member.getEmergencyContactNumber();
            default -> null;
        };
    }

    // ---- Private helpers ----

    private Member parseRow(Row row, int index) {
        Member m = new Member();
        m.setRowIndex(index);
        m.setLicenseNumber(getCellStringValue(row.getCell(0)));
        m.setLicenseClass(getCellStringValue(row.getCell(1)));
        m.setLicenseExpiry(getCellStringValue(row.getCell(2)));
        m.setGivenName(getCellStringValue(row.getCell(3)));
        m.setFamilyName(getCellStringValue(row.getCell(4)));
        m.setBirthDate(getCellStringValue(row.getCell(5)));
        m.setGender(getCellStringValue(row.getCell(6)));
        m.setActive(getCellStringValue(row.getCell(7)));
        m.setPlate20(getCellStringValue(row.getCell(8)));
        m.setPlate24(getCellStringValue(row.getCell(9)));
        m.setPlateRetro(getCellStringValue(row.getCell(10)));
        m.setPlateOpen(getCellStringValue(row.getCell(11)));
        m.setTransponder20(getCellStringValue(row.getCell(12)));
        m.setTransponder24(getCellStringValue(row.getCell(13)));
        m.setTransponderRetro(getCellStringValue(row.getCell(14)));
        m.setTransponderOpen(getCellStringValue(row.getCell(15)));
        m.setLicenseCountryCode(getCellStringValue(row.getCell(16)));
        m.setInternationalLicense(getCellStringValue(row.getCell(17)));
        m.setClubName(getCellStringValue(row.getCell(18)));
        m.setTeamName1(getCellStringValue(row.getCell(19)));
        m.setTeamName2(getCellStringValue(row.getCell(20)));
        m.setTeamName3(getCellStringValue(row.getCell(21)));
        m.setTeamName4(getCellStringValue(row.getCell(22)));
        m.setTeamName5(getCellStringValue(row.getCell(23)));
        m.setEmail(getCellStringValue(row.getCell(24)));
        m.setCcEmail(getCellStringValue(row.getCell(25)));
        m.setEmergencyContactPerson(getCellStringValue(row.getCell(26)));
        m.setEmergencyContactNumber(getCellStringValue(row.getCell(27)));
        return m;
    }

    private void writeMemberToRow(Member m, Row row) {
        setCellValue(row, 0, m.getLicenseNumber());
        setCellValue(row, 1, m.getLicenseClass());
        setCellValue(row, 2, m.getLicenseExpiry());
        setCellValue(row, 3, m.getGivenName());
        setCellValue(row, 4, m.getFamilyName());
        setCellValue(row, 5, m.getBirthDate());
        setCellValue(row, 6, m.getGender());
        setCellValue(row, 7, m.getActive());
        setCellValue(row, 8, m.getPlate20());
        setCellValue(row, 9, m.getPlate24());
        setCellValue(row, 10, m.getPlateRetro());
        setCellValue(row, 11, m.getPlateOpen());
        setCellValue(row, 12, m.getTransponder20());
        setCellValue(row, 13, m.getTransponder24());
        setCellValue(row, 14, m.getTransponderRetro());
        setCellValue(row, 15, m.getTransponderOpen());
        setCellValue(row, 16, m.getLicenseCountryCode());
        setCellValue(row, 17, m.getInternationalLicense());
        setCellValue(row, 18, m.getClubName());
        setCellValue(row, 19, m.getTeamName1());
        setCellValue(row, 20, m.getTeamName2());
        setCellValue(row, 21, m.getTeamName3());
        setCellValue(row, 22, m.getTeamName4());
        setCellValue(row, 23, m.getTeamName5());
        setCellValue(row, 24, m.getEmail());
        setCellValue(row, 25, m.getCcEmail());
        setCellValue(row, 26, m.getEmergencyContactPerson());
        setCellValue(row, 27, m.getEmergencyContactNumber());
    }

    private void copyRow(Row source, Row target) {
        if (source == null) return;
        for (int i = 0; i < EXPECTED_COLUMNS; i++) {
            Cell sourceCell = source.getCell(i);
            Cell targetCell = target.createCell(i);
            if (sourceCell != null) {
                switch (sourceCell.getCellType()) {
                    case STRING -> targetCell.setCellValue(sourceCell.getStringCellValue());
                    case NUMERIC -> {
                        if (DateUtil.isCellDateFormatted(sourceCell)) {
                            targetCell.setCellValue(sourceCell.getDateCellValue());
                        } else {
                            targetCell.setCellValue(sourceCell.getNumericCellValue());
                        }
                    }
                    case BOOLEAN -> targetCell.setCellValue(sourceCell.getBooleanCellValue());
                    case FORMULA -> targetCell.setCellFormula(sourceCell.getCellFormula());
                    default -> targetCell.setCellValue("");
                }
            }
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> {
                String val = cell.getStringCellValue();
                yield (val != null && !val.isBlank()) ? val.trim() : null;
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double numVal = cell.getNumericCellValue();
                if (numVal == Math.floor(numVal) && !Double.isInfinite(numVal)) {
                    yield String.valueOf((long) numVal);
                }
                yield String.valueOf(numVal);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getStringCellValue();
            default -> null;
        };
    }

    private void setCellValue(Row row, int col, String value) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < EXPECTED_COLUMNS; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellStringValue(cell);
                if (val != null && !val.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }
}
