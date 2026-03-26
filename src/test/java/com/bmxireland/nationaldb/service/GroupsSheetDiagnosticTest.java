package com.bmxireland.nationaldb.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Diagnostic test — reads MemberDatabase.xlsx directly and prints the Groups sheet
 * structure so we can verify the column layout.
 */
class GroupsSheetDiagnosticTest {

    private static final String DB_PATH = "MemberDatabase.xlsx";

    @Test
    void groupNamesLoadedCorrectly() throws Exception {
        File file = new File(DB_PATH);
        assumeTrue(file.exists(), "MemberDatabase.xlsx not present — skipping");

        DatabaseService svc = new DatabaseService();
        // Use reflection to set databaseFilePath since it's @Value-injected
        var field = DatabaseService.class.getDeclaredField("databaseFilePath");
        field.setAccessible(true);
        field.set(svc, DB_PATH);

        svc.loadDatabase();

        assertFalse(svc.validGroupNames.isEmpty(), "validGroupNames should not be empty");
        System.out.println("Loaded " + svc.validGroupNames.size() + " group names: " + svc.validGroupNames);

        // Spot-check the fuzzy matcher with a known example
        String resolved = svc.resolveClubName("Lucan BMX Club");
        System.out.println("'Lucan BMX Club' resolved to: " + resolved);
        assertNotEquals("Other", resolved, "Expected a real group, not 'Other'");
    }

    @Test
    void printGroupsSheetContents() throws Exception {
        File file = new File(DB_PATH);
        assumeTrue(file.exists(), "MemberDatabase.xlsx not present — skipping diagnostic");

        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {

            System.out.println("=== Sheets in workbook ===");
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                System.out.println("  [" + i + "] " + wb.getSheetName(i));
            }

            Sheet groups = wb.getSheet("Groups");
            assertNotNull(groups, "No sheet named 'Groups' found");

            System.out.println("\n=== Groups sheet — first 5 rows, first 5 columns ===");
            int rowLimit = Math.min(5, groups.getLastRowNum() + 1);
            for (int r = 0; r < rowLimit; r++) {
                Row row = groups.getRow(r);
                if (row == null) { System.out.println("  row " + r + ": (null)"); continue; }
                StringBuilder sb = new StringBuilder("  row " + r + ": ");
                for (int c = 0; c < 5; c++) {
                    Cell cell = row.getCell(c);
                    String val = cell == null ? "(null)" : getCellString(cell);
                    sb.append("[").append(c).append("]=").append(val).append("  ");
                }
                System.out.println(sb);
            }
        }
    }

    private void assumeTrue(boolean condition, String message) {
        org.junit.jupiter.api.Assumptions.assumeTrue(condition, message);
    }

    private String getCellString(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING  -> "\"" + cell.getStringCellValue() + "\"";
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK   -> "(blank)";
            default      -> "(" + cell.getCellType() + ")";
        };
    }
}
